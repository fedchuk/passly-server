package com.passly.data.order

import com.passly.domain.common.Money
import com.passly.domain.common.TaxRate
import com.passly.domain.floor.DiningTableId
import com.passly.domain.menu.MenuItemId
import com.passly.domain.menu.ModifierId
import com.passly.domain.menu.StationId
import com.passly.domain.order.*
import com.passly.domain.user.UserId
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId

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
    it.appendNewLines(this)
    it.appendNewPayments(this)
}

fun OrderEntity.updateFrom(order: Order) {
    status = order.status
    guests = order.guests
    closedBy = order.closedBy?.value
    closedAt = order.closedAt
    appendNewLines(order)
    appendNewPayments(order)
}

private fun OrderEntity.appendNewLines(order: Order) {
    val stored = lines.map { it.id }.toSet()
    order.lines.forEachIndexed { position, line ->
        if (line.id.value !in stored) lines += line.toEntity(this, position)
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
    takenBy = UserId(takenBy),
    takenAt = takenAt,
)

private fun Payment.toEntity(order: OrderEntity, position: Int) = OrderPaymentEntity(
    id = id.value,
    parentOrder = order,
    position = position,
    method = method,
    amount = amount.amount,
    takenBy = takenBy.value,
    takenAt = takenAt,
)
