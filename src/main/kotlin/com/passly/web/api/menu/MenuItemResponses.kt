package com.passly.web.api.menu

import com.passly.domain.menu.MenuItem
import com.passly.domain.menu.MenuTree
import com.passly.domain.menu.stationFor
import com.passly.domain.menu.taxRateFor
import io.swagger.v3.oas.annotations.media.Schema
import java.util.*

data class MenuItemResponse(
    val id: UUID,
    val categoryId: UUID,
    val name: String,
    val description: String?,
    @Schema(example = "4.50")
    val price: String,
    val stationId: UUID?,
    val effectiveStationId: UUID?,
    @Schema(description = "Own VAT rate of the item, empty when it uses the rate of the category")
    val taxRateId: UUID?,
    val effectiveTaxRateId: UUID?,
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
    stationId = stationId?.value,
    effectiveStationId = tree.stationFor(this)?.value,
    taxRateId = taxRateId?.value,
    effectiveTaxRateId = tree.taxRateFor(this)?.value,
    sortOrder = sortOrder,
    available = available,
    modifierGroupIds = modifierGroupIds.map { it.value },
    version = checkNotNull(version) { "Menu item $id is not stored yet" },
)
