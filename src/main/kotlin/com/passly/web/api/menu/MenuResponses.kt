package com.passly.web.api.menu

import com.passly.application.menu.*
import io.swagger.v3.oas.annotations.media.Schema
import java.util.*

data class MenuResponse(
    val venueId: UUID,
    @Schema(description = "Grows with every change of the menu. The same value is sent as the ETag.")
    val menuVersion: Long,
    @Schema(description = "Active categories only, nested, with their items in display order")
    val categories: List<MenuCategoryResponse>,
    @Schema(description = "Groups referenced by modifierGroupIds of the items")
    val modifierGroups: List<MenuModifierGroupResponse>,
)

data class MenuCategoryResponse(
    val id: UUID,
    val name: String,
    val items: List<MenuItemEntryResponse>,
    val children: List<MenuCategoryResponse>,
)

data class MenuItemEntryResponse(
    val id: UUID,
    val name: String,
    val description: String?,
    @Schema(example = "24.00")
    val price: String,
    @Schema(example = "10.00")
    val taxRate: String,
    @Schema(description = "False while the item is on the stop list")
    val available: Boolean,
    val modifierGroupIds: List<UUID>,
)

data class MenuModifierGroupResponse(
    val id: UUID,
    val name: String,
    val minSelect: Int,
    val maxSelect: Int,
    val options: List<ModifierResponse>,
)

data class MenuChangeNotice(
    val menuVersion: Long,
    @Schema(description = "What changed. Empty in the first event, which only reports the current version.")
    val change: MenuChangeKind?,
)

fun WaiterMenu.toResponse() = MenuResponse(
    venueId = venueId,
    menuVersion = menuVersion,
    categories = categories.map { it.toResponse() },
    modifierGroups = modifierGroups.map { it.toResponse() },
)

private fun WaiterMenuCategory.toResponse(): MenuCategoryResponse = MenuCategoryResponse(
    id = id,
    name = name,
    items = items.map { it.toResponse() },
    children = children.map { it.toResponse() },
)

private fun WaiterMenuItem.toResponse() = MenuItemEntryResponse(
    id = id,
    name = name,
    description = description,
    price = price.toPlainString(),
    taxRate = taxRate.toPlainString(),
    available = available,
    modifierGroupIds = modifierGroupIds,
)

private fun WaiterMenuModifierGroup.toResponse() = MenuModifierGroupResponse(
    id = id,
    name = name,
    minSelect = minSelect,
    maxSelect = maxSelect,
    options = options.map { ModifierResponse(it.id, it.name, it.priceDelta.toPlainString()) },
)
