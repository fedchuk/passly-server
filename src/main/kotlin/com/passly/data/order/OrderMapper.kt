package com.passly.data.order

import com.passly.data.voucher.voucherValueOf
import com.passly.domain.common.Money
import com.passly.domain.common.TaxRate
import com.passly.domain.discount.DiscountPercent
import com.passly.domain.discount.VenueDiscountId
import com.passly.domain.floor.DiningTableId
import com.passly.domain.menu.MenuItemId
import com.passly.domain.menu.ModifierId
import com.passly.domain.menu.StationId
import com.passly.domain.order.*
import com.passly.domain.user.UserId
import com.passly.domain.venue.CurrencyCode
import com.passly.domain.venue.ExchangeRate
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId
import com.passly.domain.voucher.VoucherCode
import com.passly.domain.voucher.VoucherId
import com.passly.domain.voucher.VoucherValue

fun OrderEntity.toDomain() = Order.fromStorage(
    id = OrderId(id),
    tenantId = TenantId(tenantId),
    venueId = VenueId(venueId),
    tableId = DiningTableId(tableId),
    status = status,
    guests = guests,
    openedBy = UserId(openedBy),
    openedAt = openedAt,
    lines = lines.sortedBy { it.position }.map { it.toDomain() },
    payments = payments.sortedBy { it.position }.map { it.toDomain() },
    closedBy = closedBy?.let(::UserId),
    closedAt = closedAt,
    version = checkNotNull(version) { "Stored order $id has no version" },
    discount = discountId?.let { discountId ->
        OrderDiscount(
            discountId = VenueDiscountId(discountId),
            name = checkNotNull(discountName) { "Discount of order $id has no name" },
            percent = DiscountPercent.of(checkNotNull(discountPercent) { "Discount of order $id has no percent" }),
            appliedBy = UserId(checkNotNull(discountAppliedBy) { "Discount of order $id has no author" }),
            appliedAt = checkNotNull(discountAppliedAt) { "Discount of order $id has no time" },
        )
    },
    voucher = voucherId?.let { voucherId ->
        OrderVoucher(
            voucherId = VoucherId(voucherId),
            code = VoucherCode.fromStorage(checkNotNull(voucherCode) { "Voucher of order $id has no code" }),
            name = checkNotNull(voucherName) { "Voucher of order $id has no name" },
            kind = checkNotNull(voucherKind) { "Voucher of order $id has no kind" },
            value = voucherValueOf(voucherPercent, voucherAmount),
            appliedBy = UserId(checkNotNull(voucherAppliedBy) { "Voucher of order $id has no author" }),
            appliedAt = checkNotNull(voucherAppliedAt) { "Voucher of order $id has no time" },
        )
    },
)

fun Order.toNewEntity() = OrderEntity(
    id = id.value,
    tenantId = tenantId.value,
    venueId = venueId.value,
    tableId = tableId.value,
    status = status,
    guests = guests,
    openedBy = openedBy.value,
    openedAt = openedAt,
).also {
    it.closedBy = closedBy?.value
    it.closedAt = closedAt
    it.copyDiscountFrom(this)
    it.copyVoucherFrom(this)
    it.syncLines(this)
    it.appendNewPayments(this)
}

fun OrderEntity.updateFrom(order: Order) {
    status = order.status
    guests = order.guests
    closedBy = order.closedBy?.value
    closedAt = order.closedAt
    copyDiscountFrom(order)
    copyVoucherFrom(order)
    syncLines(order)
    appendNewPayments(order)
}

private fun OrderEntity.copyDiscountFrom(order: Order) {
    val discount = order.discount
    discountId = discount?.discountId?.value
    discountName = discount?.name
    discountPercent = discount?.percent?.value
    discountAppliedBy = discount?.appliedBy?.value
    discountAppliedAt = discount?.appliedAt
}

private fun OrderEntity.copyVoucherFrom(order: Order) {
    val voucher = order.voucher
    voucherId = voucher?.voucherId?.value
    voucherCode = voucher?.code?.value
    voucherName = voucher?.name
    voucherKind = voucher?.kind
    voucherPercent = (voucher?.value as? VoucherValue.Percent)?.percent?.value
    voucherAmount = (voucher?.value as? VoucherValue.Amount)?.amount?.amount
    voucherAppliedBy = voucher?.appliedBy?.value
    voucherAppliedAt = voucher?.appliedAt
}

private fun OrderEntity.syncLines(order: Order) {
    val stored = lines.associateBy { it.id }
    order.lines.forEachIndexed { position, line ->
        val entity = stored[line.id.value]
        if (entity == null) {
            lines += line.toEntity(this, position)
        } else if (entity.prepStatus != line.status) {
            entity.prepStatus = line.status
            entity.statusChangedAt = line.statusChangedAt
        }
    }
}

private fun OrderLineEntity.toDomain() = OrderLine(
    id = OrderLineId(id),
    round = round,
    menuItemId = MenuItemId(menuItemId),
    name = name,
    unitPrice = Money.of(unitPrice),
    taxRate = TaxRate.of(taxRate),
    stationId = stationId?.let(::StationId),
    quantity = quantity,
    comment = comment,
    modifiers = modifiers.map {
        OrderLineModifier(
            ModifierId(it.modifierId),
            it.groupName,
            it.name,
            Money.of(it.priceDelta)
        )
    },
    addedBy = UserId(addedBy),
    addedAt = addedAt,
    status = prepStatus,
    statusChangedAt = statusChangedAt,
)

private fun OrderLine.toEntity(order: OrderEntity, position: Int) = OrderLineEntity(
    id = id.value,
    parentOrder = order,
    position = position,
    round = round,
    menuItemId = menuItemId.value,
    name = name,
    unitPrice = unitPrice.amount,
    taxRate = taxRate.percent,
    stationId = stationId?.value,
    quantity = quantity,
    comment = comment,
    addedBy = addedBy.value,
    addedAt = addedAt,
).also { entity ->
    entity.prepStatus = status
    entity.statusChangedAt = statusChangedAt
    entity.modifiers = modifiers.map {
        OrderLineModifierEmbeddable(it.modifierId.value, it.groupName, it.name, it.priceDelta.amount)
    }.toMutableList()
}

private fun OrderEntity.appendNewPayments(order: Order) {
    val stored = payments.map { it.id }.toSet()
    order.payments.forEachIndexed { position, payment ->
        if (payment.id.value !in stored) payments += payment.toEntity(this, position)
    }
}

private fun OrderPaymentEntity.toDomain() = Payment(
    id = PaymentId(id),
    method = method,
    amount = Money.of(amount),
    tender = Tender(Money.of(tenderedAmount), CurrencyCode.of(tenderedCurrency), exchangeRate?.let(ExchangeRate::of)),
    change = Money.of(changeGiven),
    takenBy = UserId(takenBy),
    takenAt = takenAt,
)

private fun Payment.toEntity(order: OrderEntity, position: Int) = OrderPaymentEntity(
    id = id.value,
    parentOrder = order,
    position = position,
    method = method,
    amount = amount.amount,
    tenderedAmount = tender.amount.amount,
    tenderedCurrency = tender.currency.value,
    exchangeRate = tender.rate?.value,
    changeGiven = change.amount,
    takenBy = takenBy.value,
    takenAt = takenAt,
)
