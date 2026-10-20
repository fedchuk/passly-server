package com.passly.web.admin

import com.passly.application.access.AccessibleVenue
import com.passly.domain.menu.CategoryId
import com.passly.domain.menu.MenuTree
import com.passly.domain.user.Permission
import com.passly.domain.user.Role
import java.util.UUID

data class VenueView(
    val id: UUID,
    val name: String,
    val role: Role,
) {
    val manageable: Boolean get() = Permission.EDIT_MENU in role.permissions
}

data class CategoryNodeView(
    val id: UUID,
    val name: String,
    val active: Boolean,
    val first: Boolean,
    val last: Boolean,
    val children: List<CategoryNodeView>,
)

fun AccessibleVenue.toView() = VenueView(venueId.value, name, role)

fun MenuTree.toNodeViews(parentId: CategoryId? = null): List<CategoryNodeView> {
    val siblings = children(parentId)
    return siblings.mapIndexed { index, category ->
        CategoryNodeView(
            id = category.id.value,
            name = category.name,
            active = category.active,
            first = index == 0,
            last = index == siblings.lastIndex,
            children = toNodeViews(category.id),
        )
    }
}
