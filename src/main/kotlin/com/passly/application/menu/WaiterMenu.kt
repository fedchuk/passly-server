package com.passly.application.menu

import java.math.BigDecimal
import java.util.*

data class WaiterMenu(
    val venueId: UUID,
    val menuVersion: Long,
    val currency: String,
    val categories: List<WaiterMenuCategory>,
    val modifierGroups: List<WaiterMenuModifierGroup>,
)

data class WaiterMenuCategory(
    val id: UUID,
    val name: String,
    val items: List<WaiterMenuItem>,
    val children: List<WaiterMenuCategory>,
)

data class WaiterMenuItem(
    val id: UUID,
    val name: String,
    val description: String?,
    val price: BigDecimal,
    val taxRate: BigDecimal,
    val available: Boolean,
    val modifierGroupIds: List<UUID>,
)

data class WaiterMenuModifierGroup(
    val id: UUID,
    val name: String,
    val minSelect: Int,
    val maxSelect: Int,
    val options: List<WaiterMenuOption>,
)

data class WaiterMenuOption(
    val id: UUID,
    val name: String,
    val priceDelta: BigDecimal,
)
