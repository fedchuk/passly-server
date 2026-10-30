package com.passly.web.kitchen

import com.passly.application.menu.WaiterMenu
import com.passly.application.menu.WaiterMenuCategory
import java.math.RoundingMode
import java.util.*

data class StopListItemView(
    val id: UUID,
    val name: String,
    val category: String,
    val price: String,
    val available: Boolean,
)

fun WaiterMenu.toStopListItems(): List<StopListItemView> =
    categories.flatMap { it.toStopListItems(path = null) }

private fun WaiterMenuCategory.toStopListItems(path: String?): List<StopListItemView> {
    val title = path?.let { "$it › $name" } ?: name
    return items.map { item ->
        StopListItemView(
            id = item.id,
            name = item.name,
            category = title,
            price = item.price.setScale(2, RoundingMode.HALF_UP).toPlainString(),
            available = item.available,
        )
    } + children.flatMap { it.toStopListItems(title) }
}

fun stopListSummary(stopped: Int): String = when (stopped) {
    0 -> "Everything can be ordered."
    1 -> "1 dish guests cannot order now"
    else -> "$stopped dishes guests cannot order now"
}
