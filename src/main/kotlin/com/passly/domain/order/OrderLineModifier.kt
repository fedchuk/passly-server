package com.passly.domain.order

import com.passly.domain.common.Money
import com.passly.domain.menu.ModifierId

data class OrderLineModifier(
    val modifierId: ModifierId,
    val groupName: String,
    val name: String,
    val priceDelta: Money,
)
