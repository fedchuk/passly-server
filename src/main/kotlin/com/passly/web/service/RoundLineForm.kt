package com.passly.web.service

import com.passly.domain.menu.MenuItemId
import com.passly.domain.menu.ModifierId
import com.passly.domain.order.OrderLineRequest
import java.util.*

class RoundLineForm {
    var menuItemId: UUID? = null
    var quantity: Int = 1
    var modifierIds: MutableList<UUID> = mutableListOf()
    var comment: String = ""

    fun toRequest(): OrderLineRequest? = menuItemId?.let { id ->
        OrderLineRequest(
            menuItemId = MenuItemId(id),
            quantity = quantity,
            modifierIds = modifierIds.map(::ModifierId),
            comment = comment.ifBlank { null },
        )
    }
}
