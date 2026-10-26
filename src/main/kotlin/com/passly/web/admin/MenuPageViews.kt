package com.passly.web.admin

import com.passly.domain.menu.*
import java.util.*
import kotlin.math.absoluteValue

data class MenuRootView(
    val id: UUID,
    val name: String,
    val initial: String,
    val hue: Int,
    val station: String?,
    val active: Boolean,
    val subcategories: Int,
    val items: Int,
    val open: Boolean,
)

data class MenuFocusView(
    val id: UUID,
    val name: String,
    val root: Boolean,
    val active: Boolean,
    val path: List<CategoryRefView>,
    val children: List<SubcategoryCardView>,
    val items: List<MenuItemCardView>,
)

data class SubcategoryCardView(
    val id: UUID,
    val name: String,
    val initial: String,
    val hue: Int,
    val active: Boolean,
    val items: Int,
    val subcategories: Int,
)

data class MenuItemCardView(
    val id: UUID,
    val name: String,
    val initial: String,
    val hue: Int,
    val description: String?,
    val price: String,
    val station: String?,
    val available: Boolean,
    val modifierGroups: Int,
)

fun MenuTree.toRootViews(itemCounts: Map<CategoryId, Int>, stations: List<Station>, openId: CategoryId?): List<MenuRootView> {
    val names = stations.associate { it.id to it.name }
    return children(null).map { category ->
        MenuRootView(
            id = category.id.value,
            name = category.name,
            initial = initialOf(category.name),
            hue = hueOf(category.name),
            station = category.stationId?.let { names[it] },
            active = category.active,
            subcategories = children(category.id).size,
            items = itemCounts[category.id] ?: 0,
            open = category.id == openId,
        )
    }
}

fun MenuTree.toFocusView(
    focusId: CategoryId,
    items: List<MenuItem>,
    itemCounts: Map<CategoryId, Int>,
    stations: List<Station>,
): MenuFocusView {
    val focus = this[focusId]
    val names: Map<StationId, String> = stations.associate { it.id to it.name }
    return MenuFocusView(
        id = focus.id.value,
        name = focus.name,
        root = focus.parentId == null,
        active = focus.active,
        path = pathTo(focusId).map { CategoryRefView(it.id.value, it.name) },
        children = children(focusId).map { child ->
            SubcategoryCardView(
                id = child.id.value,
                name = child.name,
                initial = initialOf(child.name),
                hue = hueOf(child.name),
                active = child.active,
                items = itemCounts[child.id] ?: 0,
                subcategories = children(child.id).size,
            )
        },
        items = items.map { item ->
            MenuItemCardView(
                id = item.id.value,
                name = item.name,
                initial = initialOf(item.name),
                hue = hueOf(item.name),
                description = item.description,
                price = item.price.toString(),
                station = stationFor(item)?.let { names[it] },
                available = item.available,
                modifierGroups = item.modifierGroupIds.size,
            )
        },
    )
}

fun MenuTree.pathTo(id: CategoryId): List<Category> =
    generateSequence(this[id]) { category -> category.parentId?.let { this[it] } }.toList().reversed()

private fun initialOf(name: String): String = name.trim().take(1).uppercase()

private fun hueOf(name: String): Int = name.lowercase().hashCode().absoluteValue % 360
