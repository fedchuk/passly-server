package com.passly.web.admin

import com.passly.domain.common.Money
import com.passly.domain.menu.ModifierGroup
import java.util.UUID

data class ModifierGroupRowView(
    val id: UUID,
    val name: String,
    val rule: String,
    val options: String,
)

fun ModifierGroup.toRowView() = ModifierGroupRowView(
    id = id.value,
    name = name,
    rule = selectionRule(minSelect, maxSelect),
    options = modifiers.joinToString(", ") { option ->
        if (option.priceDelta.amount.signum() == 0) option.name else "${option.name} (${option.priceDelta.signed()})"
    },
)

private fun selectionRule(min: Int, max: Int) = when {
    min == 0 && max == 1 -> "Optional, one"
    min == 0 -> "Optional, up to $max"
    min == max -> "Choose $min"
    else -> "Choose $min to $max"
}

private fun Money.signed() = if (isNegative) toString() else "+$this"
