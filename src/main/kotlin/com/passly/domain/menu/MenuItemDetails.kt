package com.passly.domain.menu

import com.passly.domain.common.Money
import com.passly.domain.common.TaxRate

data class MenuItemDetails(
    val name: String,
    val description: String?,
    val price: Money,
    val taxRate: TaxRate,
    val stationId: StationId? = null,
)
