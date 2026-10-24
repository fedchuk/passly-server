package com.passly.domain.order

import com.passly.domain.common.Money
import com.passly.domain.common.TaxRate
import com.passly.domain.menu.MenuItemId
import com.passly.domain.menu.StationId

data class PricedLine(
    val menuItemId: MenuItemId,
    val name: String,
    val unitPrice: Money,
    val taxRate: TaxRate,
    val stationId: StationId?,
    val quantity: Int,
    val comment: String?,
    val modifiers: List<OrderLineModifier>,
)
