package com.passly.web.api.menu

import com.passly.domain.menu.MenuItem
import com.passly.domain.menu.MenuTree
import com.passly.domain.menu.stationFor
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

data class MenuItemResponse(
    val id: UUID,
    val categoryId: UUID,
    val name: String,
    val description: String?,
    @Schema(example = "4.50")
    val price: String,
    @Schema(example = "10.00")
    val taxRate: String,
    val stationId: UUID?,
    val effectiveStationId: UUID?,
    val sortOrder: Int,
    val available: Boolean,
    val modifierGroupIds: List<UUID>,
    val version: Long,
)

fun MenuItem.toResponse(tree: MenuTree) = MenuItemResponse(
    id = id.value,
    categoryId = checkNotNull(categoryId) { "Active menu item $id has no category" }.value,
    name = name,
    description = description,
    price = price.amount.toPlainString(),
    taxRate = taxRate.percent.toPlainString(),
    stationId = stationId?.value,
    effectiveStationId = tree.stationFor(this)?.value,
    sortOrder = sortOrder,
    available = available,
    modifierGroupIds = modifierGroupIds.map { it.value },
    version = checkNotNull(version) { "Menu item $id is not stored yet" },
)
