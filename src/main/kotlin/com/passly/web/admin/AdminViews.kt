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
    val children: List<CategoryNodeView>,
)

fun AccessibleVenue.toView() = VenueView(venueId.value, name, role)

fun MenuTree.toNodeViews(parentId: CategoryId? = null): List<CategoryNodeView> =
    children(parentId).map { CategoryNodeView(it.id.value, it.name, it.active, toNodeViews(it.id)) }
