package com.passly.application.order

import com.passly.domain.common.*
import com.passly.domain.discount.DiscountRepository
import com.passly.domain.floor.FloorPlanRepository
import com.passly.domain.menu.*
import com.passly.domain.order.*
import com.passly.domain.tax.TaxRateRepository
import com.passly.domain.venue.*
import com.passly.domain.voucher.VoucherRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
@Transactional
class OrderService(
    private val venues: VenueRepository,
    private val floors: FloorPlanRepository,
    private val orders: OrderRepository,
    private val items: MenuItemRepository,
    private val modifierGroups: ModifierGroupRepository,
    private val categories: CategoryRepository,
    private val settings: VenueSettingsRepository,
    private val taxRates: TaxRateRepository,
    private val discounts: DiscountRepository,
    private val vouchers: VoucherRepository,
    private val changes: OrderChangeRecorder,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun get(venueId: VenueId, id: OrderId): Order =
        orders.find(venueId, id) ?: throw NotFoundException("Order", id)

    @Transactional(readOnly = true)
    fun listOpen(venueId: VenueId): List<Order> {
        requireVenue(venueId)
        return orders.findOpen(venueId)
    }

    fun open(command: OpenOrderCommand): Order {
        val venue = requireVenue(command.venueId)
        val table = floors.load(venue.id).table(command.tableId)
        if (orders.findOpenAtTable(table.id) != null) {
            throw ConflictException("Table ${table.label} already has an open order")
        }
        val now = clock.instant()
        val order = Order.open(table, command.guests, command.waiterId, now)
        if (command.lines.isNotEmpty()) order.addRound(price(venue.id, command.lines), command.waiterId, now)
        orders.save(order)
        if (order.lines.isEmpty()) changes.recordChange(order, OrderChangeKind.OPENED)
        else changes.recordChange(order, OrderChangeKind.ROUND_SENT, order.stationsOfRound(order.rounds))
        return order
    }

    fun addRound(command: AddRoundCommand): Order {
        val order = lockOrder(command.venueId, command.orderId)
        val round = order.addRound(price(command.venueId, command.lines), command.waiterId, clock.instant())
        orders.save(order)
        changes.recordChange(order, OrderChangeKind.ROUND_SENT, order.stationsOfRound(round))
        return order
    }

    fun applyDiscount(command: ApplyDiscountCommand): Order {
        val order = lockOrder(command.venueId, command.orderId)
        val discount = discounts.load(command.venueId)[command.discountId]
        order.applyDiscount(discount, command.appliedBy, clock.instant())
        orders.save(order)
        changes.recordChange(order, OrderChangeKind.BILL_CHANGED)
        return order
    }

    fun removeDiscount(command: RemoveDiscountCommand): Order {
        val order = lockOrder(command.venueId, command.orderId)
        order.removeDiscount()
        orders.save(order)
        changes.recordChange(order, OrderChangeKind.BILL_CHANGED)
        return order
    }

    fun applyVoucher(command: ApplyVoucherCommand): Order {
        val order = lockOrder(command.venueId, command.orderId)
        val voucher = vouchers.findByCodeForUpdate(command.venueId, command.code)
            ?: throw NotFoundException("Voucher", command.code)
        val now = clock.instant()
        val previous = order.voucher
        order.applyVoucher(voucher, command.appliedBy, now)
        voucher.redeem(order.id, now, venueSettings(command.venueId).today(now))
        if (previous != null && previous.voucherId != voucher.id) release(order, previous)
        vouchers.save(voucher)
        orders.save(order)
        changes.recordChange(order, OrderChangeKind.BILL_CHANGED)
        return order
    }

    fun removeVoucher(command: RemoveVoucherCommand): Order {
        val order = lockOrder(command.venueId, command.orderId)
        val applied = order.voucher ?: return order
        order.removeVoucher()
        release(order, applied)
        orders.save(order)
        changes.recordChange(order, OrderChangeKind.BILL_CHANGED)
        return order
    }

    fun pay(command: PayOrderCommand): Order {
        val order = lockOrder(command.venueId, command.orderId)
        order.pay(command.method, tender(command.venueId, command.amount, command.currency), command.takenBy, clock.instant())
        orders.save(order)
        changes.recordChange(order, if (order.status == OrderStatus.CLOSED) OrderChangeKind.CLOSED else OrderChangeKind.PAID)
        return order
    }

    fun close(command: CloseOrderCommand): Order {
        val order = lockOrder(command.venueId, command.orderId)
        order.close(command.closedBy, clock.instant())
        orders.save(order)
        changes.recordChange(order, OrderChangeKind.CLOSED)
        return order
    }

    private fun Order.stationsOfRound(round: Int): Set<StationId> =
        lines.filter { it.round == round }.mapNotNull { it.stationId }.toSet()

    private fun release(order: Order, applied: OrderVoucher) {
        val voucher = vouchers.findForUpdate(order.venueId, applied.voucherId) ?: return
        voucher.release(order.id)
        vouchers.save(voucher)
    }

    private fun venueSettings(venueId: VenueId): VenueSettings =
        settings.find(venueId) ?: throw NotFoundException("Venue", venueId)

    private fun tender(venueId: VenueId, amount: Money, currency: CurrencyCode?): Tender {
        val venueSettings = venueSettings(venueId)
        if (currency == null || currency == venueSettings.currency) return Tender.main(amount, venueSettings.currency)
        val accepted = venueSettings.acceptedCurrencies.firstOrNull { it.currency == currency }
            ?: throw ValidationException("$currency is not accepted here", "currency")
        return Tender(amount, currency, accepted.rate)
    }

    private fun lockOrder(venueId: VenueId, orderId: OrderId): Order =
        orders.findForUpdate(venueId, orderId) ?: throw NotFoundException("Order", orderId)

    private fun price(venueId: VenueId, requests: List<OrderLineRequest>): List<PricedLine> {
        if (requests.isEmpty()) throw ValidationException("A round needs at least one line", "lines")
        val requestedIds = requests.map { it.menuItemId }.distinct()
        val found = items.findActiveByIds(venueId, requestedIds).associateBy { it.id }

        val missing = requestedIds.filter { it !in found }
        val stopListed = found.values.filter { !it.available }
        if (missing.isNotEmpty() || stopListed.isNotEmpty()) {
            throw UnavailableItemsException(
                itemIds = (missing + stopListed.map { it.id }).map { it.value },
                names = stopListed.map { it.name } + missing.map { "an item removed from the menu" },
            )
        }

        val groups = modifierGroups.findAll(venueId).associateBy { it.id }
        val tree = categories.loadTree(venueId)
        val rates = taxRates.load(venueId)
        return requests.map { request ->
            val item = found.getValue(request.menuItemId)
            val taxRateId = checkNotNull(tree.taxRateFor(item)) { "Menu item ${item.id} has no tax rate" }
            item.priceLine(request, groups, tree.stationFor(item), rates[taxRateId].rate)
        }
    }

    private fun requireVenue(id: VenueId): Venue =
        venues.findById(id) ?: throw NotFoundException("Venue", id)
}
