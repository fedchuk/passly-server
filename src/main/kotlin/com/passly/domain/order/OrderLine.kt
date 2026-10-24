package com.passly.domain.order

import com.passly.domain.common.Money
import com.passly.domain.common.TaxRate
import com.passly.domain.menu.MenuItemId
import com.passly.domain.menu.StationId
import com.passly.domain.user.UserId
import java.time.Instant

data class OrderLine(
    val id: OrderLineId,
    val round: Int,
    val menuItemId: MenuItemId,
    val name: String,
    val unitPrice: Money,
    val taxRate: TaxRate,
    val stationId: StationId?,
    val quantity: Int,
    val comment: String?,
    val modifiers: List<OrderLineModifier>,
    val addedBy: UserId,
    val addedAt: Instant,
) {
    val total: Money get() = unitPrice * quantity
}
