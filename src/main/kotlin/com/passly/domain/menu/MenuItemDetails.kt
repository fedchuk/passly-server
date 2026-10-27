package com.passly.domain.menu

import com.passly.domain.common.Money
import com.passly.domain.tax.VenueTaxRateId

data class MenuItemDetails(
    val name: String,
    val description: String?,
    val price: Money,
    val stationId: StationId? = null,
    val taxRateId: VenueTaxRateId? = null,
)
