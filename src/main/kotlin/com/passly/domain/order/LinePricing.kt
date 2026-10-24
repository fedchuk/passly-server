package com.passly.domain.order

import com.passly.domain.common.ValidationException
import com.passly.domain.menu.MenuItem
import com.passly.domain.menu.ModifierGroup
import com.passly.domain.menu.ModifierGroupId
import com.passly.domain.menu.StationId

const val MAX_LINE_QUANTITY = 99
const val MAX_LINE_COMMENT_LENGTH = 200

fun MenuItem.priceLine(
    request: OrderLineRequest,
    groups: Map<ModifierGroupId, ModifierGroup>,
    stationId: StationId?,
): PricedLine {
    require(request.menuItemId == id) { "Request for ${request.menuItemId} priced with item $id" }
    if (request.quantity !in 1..MAX_LINE_QUANTITY) {
        throw ValidationException("Quantity of '$name' must be from 1 to $MAX_LINE_QUANTITY", "quantity")
    }
    val comment = request.comment?.trim()?.ifEmpty { null }
    if (comment != null && comment.length > MAX_LINE_COMMENT_LENGTH) {
        throw ValidationException("Comment for '$name' is too long (max $MAX_LINE_COMMENT_LENGTH)", "comment")
    }

    val attached = modifierGroupIds.map { groupId ->
        checkNotNull(groups[groupId]) { "Modifier group $groupId of item $id is missing" }
    }
    val known = attached.flatMap { group -> group.modifiers.map { it.id } }.toSet()
    request.modifierIds.firstOrNull { it !in known }?.let {
        throw ValidationException("Option $it cannot be chosen for '$name'", "modifierIds")
    }

    val modifiers = attached.flatMap { group ->
        val groupOptionIds = group.modifiers.map { it.id }.toSet()
        group.select(request.modifierIds.filter { it in groupOptionIds })
            .map { OrderLineModifier(it.id, group.name, it.name, it.priceDelta) }
    }
    val unitPrice = modifiers.fold(price) { sum, modifier -> sum + modifier.priceDelta }
    if (unitPrice.isNegative) throw ValidationException("Price of '$name' with the chosen options is negative")

    return PricedLine(
        menuItemId = id,
        name = name,
        unitPrice = unitPrice,
        taxRate = taxRate,
        stationId = stationId,
        quantity = request.quantity,
        comment = comment,
        modifiers = modifiers,
    )
}
