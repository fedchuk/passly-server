package com.passly.domain.order

import com.passly.domain.common.ConflictException
import com.passly.domain.common.Money
import com.passly.domain.common.ValidationException
import com.passly.domain.floor.DiningTable
import com.passly.domain.floor.DiningTableId
import com.passly.domain.user.UserId
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId
import java.time.Instant

class Order private constructor(
    val id: OrderId,
    val tenantId: TenantId,
    val venueId: VenueId,
    val tableId: DiningTableId,
    status: OrderStatus,
    val guests: Int?,
    val openedBy: UserId,
    val openedAt: Instant,
    lines: List<OrderLine>,
    payments: List<Payment>,
    closedBy: UserId?,
    closedAt: Instant?,
    val version: Long?,
) {
    var status: OrderStatus = status
        private set

    var lines: List<OrderLine> = lines
        private set

    var payments: List<Payment> = payments
        private set

    var closedBy: UserId? = closedBy
        private set

    var closedAt: Instant? = closedAt
        private set

    val isNew: Boolean get() = version == null

    val rounds: Int get() = lines.maxOfOrNull { it.round } ?: 0

    val total: Money get() = lines.fold(Money.ZERO) { sum, line -> sum + line.total }

    val paid: Money get() = payments.fold(Money.ZERO) { sum, payment -> sum + payment.amount }

    val due: Money get() = total - paid

    val taxShares: List<TaxShare>
        get() = lines.groupBy { it.taxRate.percent }
            .toSortedMap()
            .map { (_, rateLines) -> TaxShare.of(rateLines.first().taxRate, rateLines.fold(Money.ZERO) { sum, line -> sum + line.total }) }

    fun addRound(priced: List<PricedLine>, addedBy: UserId, addedAt: Instant): Int {
        requireOpen()
        if (priced.isEmpty()) throw ValidationException("A round needs at least one line", "lines")
        if (priced.size > MAX_LINES_PER_ROUND) {
            throw ValidationException("A round can have at most $MAX_LINES_PER_ROUND lines", "lines")
        }
        if (lines.size + priced.size > MAX_LINES) {
            throw ValidationException("An order can have at most $MAX_LINES lines", "lines")
        }
        val round = rounds + 1
        lines = lines + priced.map { line ->
            OrderLine(
                id = OrderLineId.new(),
                round = round,
                menuItemId = line.menuItemId,
                name = line.name,
                unitPrice = line.unitPrice,
                taxRate = line.taxRate,
                stationId = line.stationId,
                quantity = line.quantity,
                comment = line.comment,
                modifiers = line.modifiers,
                addedBy = addedBy,
                addedAt = addedAt,
            )
        }
        return round
    }

    fun pay(method: PaymentMethod, amount: Money, takenBy: UserId, takenAt: Instant): Payment {
        requireOpen()
        if (lines.isEmpty()) throw ValidationException("There is nothing to pay yet", "amount")
        if (amount.isNegative || amount.isZero) throw ValidationException("Amount must be more than zero", "amount")
        if (amount > due) throw ValidationException("Amount $amount is more than the $due due", "amount")
        if (payments.size >= MAX_PAYMENTS) {
            throw ValidationException("An order can have at most $MAX_PAYMENTS payments", "amount")
        }
        val payment = Payment(PaymentId.new(), method, amount, takenBy, takenAt)
        payments = payments + payment
        if (due.isZero) markClosed(takenBy, takenAt)
        return payment
    }

    fun close(by: UserId, at: Instant) {
        requireOpen()
        if (!due.isZero) throw ConflictException("Table still has $due to pay")
        markClosed(by, at)
    }

    private fun markClosed(by: UserId, at: Instant) {
        status = OrderStatus.CLOSED
        closedBy = by
        closedAt = at
    }

    private fun requireOpen() {
        if (status != OrderStatus.OPEN) throw ConflictException("This order is already closed")
    }

    override fun equals(other: Any?) = other is Order && other.id == id

    override fun hashCode() = id.hashCode()

    override fun toString() = "Order($id, table=$tableId, $status, ${lines.size} lines, ${payments.size} payments)"

    companion object {
        const val MAX_GUESTS = 50
        const val MAX_LINES_PER_ROUND = 100
        const val MAX_LINES = 500
        const val MAX_PAYMENTS = 20

        fun open(table: DiningTable, guests: Int?, openedBy: UserId, openedAt: Instant): Order {
            if (!table.active) throw ConflictException("Table ${table.label} is inactive")
            if (guests != null && guests !in 1..MAX_GUESTS) {
                throw ValidationException("Guests must be from 1 to $MAX_GUESTS", "guests")
            }
            return Order(
                id = OrderId.new(),
                tenantId = table.tenantId,
                venueId = table.venueId,
                tableId = table.id,
                status = OrderStatus.OPEN,
                guests = guests,
                openedBy = openedBy,
                openedAt = openedAt,
                lines = emptyList(),
                payments = emptyList(),
                closedBy = null,
                closedAt = null,
                version = null,
            )
        }

        fun fromStorage(
            id: OrderId,
            tenantId: TenantId,
            venueId: VenueId,
            tableId: DiningTableId,
            status: OrderStatus,
            guests: Int?,
            openedBy: UserId,
            openedAt: Instant,
            lines: List<OrderLine>,
            payments: List<Payment>,
            closedBy: UserId?,
            closedAt: Instant?,
            version: Long,
        ) = Order(id, tenantId, venueId, tableId, status, guests, openedBy, openedAt, lines, payments, closedBy, closedAt, version)
    }
}
