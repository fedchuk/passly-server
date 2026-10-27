package com.passly.web.admin

import com.passly.domain.menu.*
import com.passly.domain.tax.VenueTaxRate
import com.passly.domain.tax.VenueTaxRateId
import java.util.*
import kotlin.math.absoluteValue

data class MenuNodeView(
    val id: UUID,
    val parentId: UUID?,
    val name: String,
    val initial: String,
    val hue: Int,
    val station: String?,
    val taxRate: String?,
    val active: Boolean,
    val subcategories: Int,
    val items: Int,
    val open: Boolean,
    val children: List<MenuNodeView>,
    val dishes: List<MenuItemCardView>,
)

data class MenuItemCardView(
    val id: UUID,
    val name: String,
    val initial: String,
    val hue: Int,
    val description: String?,
    val price: String,
    val station: String?,
    val taxRate: String?,
    val available: Boolean,
    val modifierGroups: Int,
)

fun MenuTree.toNodeViews(
    openPath: Set<CategoryId>,
    itemCounts: Map<CategoryId, Int>,
    stations: List<Station>,
    taxRates: List<VenueTaxRate>,
    itemsOf: (CategoryId) -> List<MenuItem>,
): List<MenuNodeView> {
    val names: Map<StationId, String> = stations.associate { it.id to it.name }
    val rateLabels: Map<VenueTaxRateId, String> = taxRates.associate { it.id to it.optionLabel }

    fun dishCard(item: MenuItem) = MenuItemCardView(
        id = item.id.value,
        name = item.name,
        initial = initialOf(item.name),
        hue = hueOf(item.name),
        description = item.description,
        price = item.price.toString(),
        station = stationFor(item)?.let { names[it] },
        taxRate = item.taxRateId?.let { rateLabels[it] },
        available = item.available,
        modifierGroups = item.modifierGroupIds.size,
    )

    fun nodes(parentId: CategoryId?): List<MenuNodeView> = children(parentId).map { category ->
        val open = category.id in openPath
        MenuNodeView(
            id = category.id.value,
            parentId = category.parentId?.value,
            name = category.name,
            initial = initialOf(category.name),
            hue = hueOf(category.name),
            station = category.stationId?.let { names[it] },
            taxRate = category.taxRateId?.let { rateLabels[it] },
            active = category.active,
            subcategories = children(category.id).size,
            items = itemCounts[category.id] ?: 0,
            open = open,
            children = if (open) nodes(category.id) else emptyList(),
            dishes = if (open) itemsOf(category.id).map { dishCard(it) } else emptyList(),
        )
    }

    return nodes(null)
}

fun MenuTree.pathTo(id: CategoryId): List<Category> =
    generateSequence(this[id]) { category -> category.parentId?.let { this[it] } }.toList().reversed()

private fun initialOf(name: String): String = name.trim().take(1).uppercase()

private fun hueOf(name: String): Int = name.lowercase().hashCode().absoluteValue % 360
