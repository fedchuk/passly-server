package com.passly.domain.order

import com.passly.domain.menu.MenuItemId
import com.passly.domain.menu.ModifierId

data class OrderLineRequest(
    val menuItemId: MenuItemId,
    val quantity: Int,
    val modifierIds: List<ModifierId> = emptyList(),
    val comment: String? = null,
)
