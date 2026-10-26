package com.passly.web.service

import com.passly.application.menu.WaiterMenu
import com.passly.application.menu.WaiterMenuCategory
import com.passly.application.menu.WaiterMenuItem
import com.passly.application.menu.WaiterMenuModifierGroup
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*

data class MenuSectionView(
    val id: UUID,
    val name: String,
    val groups: List<MenuGroupView>,
)

data class MenuGroupView(
    val title: String?,
    val items: List<MenuItemTileView>,
)

data class MenuItemTileView(
    val id: UUID,
    val name: String,
    val description: String?,
    val price: String,
    val available: Boolean,
    val modifierGroupIds: String,
)

data class ModifierGroupView(
    val id: UUID,
    val name: String,
    val minSelect: Int,
    val maxSelect: Int,
    val single: Boolean,
    val hint: String,
    val options: List<ModifierOptionView>,
)

data class ModifierOptionView(
    val id: UUID,
    val name: String,
    val priceDelta: String,
    val priceLabel: String?,
)

fun WaiterMenu.toSectionViews(): List<MenuSectionView> =
    categories.map { category ->
        MenuSectionView(category.id, category.name, category.toGroupViews(title = null))
    }.filter { section -> section.groups.isNotEmpty() }

fun WaiterMenu.toModifierGroupViews(): List<ModifierGroupView> =
    modifierGroups.map { it.toView() }

private fun WaiterMenuCategory.toGroupViews(title: String?): List<MenuGroupView> {
    val own = if (items.isEmpty()) emptyList() else listOf(MenuGroupView(title, items.map { it.toTileView() }))
    return own + children.flatMap { child ->
        child.toGroupViews(title?.let { "$it › ${child.name}" } ?: child.name)
    }
}

private fun WaiterMenuItem.toTileView() = MenuItemTileView(
    id = id,
    name = name,
    description = description,
    price = price.toMoneyString(),
    available = available,
    modifierGroupIds = modifierGroupIds.joinToString(","),
)

private fun WaiterMenuModifierGroup.toView() = ModifierGroupView(
    id = id,
    name = name,
    minSelect = minSelect,
    maxSelect = maxSelect,
    single = minSelect == 1 && maxSelect == 1,
    hint = selectionHint(minSelect, maxSelect),
    options = options.map { option ->
        ModifierOptionView(
            id = option.id,
            name = option.name,
            priceDelta = option.priceDelta.toMoneyString(),
            priceLabel = when (option.priceDelta.signum()) {
                1 -> "+${option.priceDelta.toMoneyString()}"
                -1 -> option.priceDelta.toMoneyString()
                else -> null
            },
        )
    },
)

private fun selectionHint(min: Int, max: Int): String = when {
    min == 0 && max == 1 -> "Optional"
    min == 0 -> "Optional, up to $max"
    min == max -> "Choose $min"
    else -> "Choose $min to $max"
}

private fun BigDecimal.toMoneyString(): String = setScale(2, RoundingMode.HALF_UP).toPlainString()
