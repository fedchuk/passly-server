package com.passly.web.admin

import com.passly.domain.menu.MenuItem
import com.passly.domain.menu.MenuTree
import com.passly.domain.menu.ModifierGroup
import com.passly.domain.menu.Station
import com.passly.domain.menu.stationFor
import java.util.UUID

data class CategoryRefView(
    val id: UUID,
    val name: String,
)

data class MenuItemRowView(
    val id: UUID,
    val name: String,
    val price: String,
    val taxRate: String,
    val station: String?,
    val stationInherited: Boolean,
    val available: Boolean,
    val modifierGroupCount: Int,
    val first: Boolean,
    val last: Boolean,
)

data class OptionView(
    val id: UUID,
    val name: String,
)

fun List<MenuItem>.toRowViews(tree: MenuTree, stations: List<Station>): List<MenuItemRowView> {
    val stationNames = stations.associate { it.id to it.name }
    return mapIndexed { index, item ->
        val station = tree.stationFor(item)
        MenuItemRowView(
            id = item.id.value,
            name = item.name,
            price = item.price.toString(),
            taxRate = item.taxRate.toString(),
            station = station?.let { stationNames[it] },
            stationInherited = item.stationId == null && station != null,
            available = item.available,
            modifierGroupCount = item.modifierGroupIds.size,
            first = index == 0,
            last = index == lastIndex,
        )
    }
}

fun Station.toOption() = OptionView(id.value, name)

fun ModifierGroup.toOption() = OptionView(id.value, name)
