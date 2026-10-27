package com.passly.web.admin

import com.passly.application.access.AccessibleVenue
import com.passly.domain.menu.CategoryId
import com.passly.domain.menu.MenuTree
import com.passly.domain.user.Permission
import java.util.*

data class VenueView(
    val id: UUID,
    val name: String,
    val roleName: String,
    val permissions: Set<Permission>,
) {
    val manageable: Boolean get() = Permission.EDIT_MENU in permissions
    val staffManageable: Boolean get() = Permission.MANAGE_STAFF in permissions
    val rolesManageable: Boolean get() = Permission.MANAGE_ROLES in permissions
    val tablesManageable: Boolean get() = Permission.MANAGE_TABLES in permissions
    val takesOrders: Boolean get() = Permission.TAKE_ORDERS in permissions
    val closesOrders: Boolean get() = Permission.CLOSE_ORDERS in permissions
    val managesStopList: Boolean get() = Permission.MANAGE_STOP_LIST in permissions
    val preparesOrders: Boolean get() = Permission.PREPARE_ORDERS in permissions
    val settingsManageable: Boolean get() = Permission.MANAGE_VENUE in permissions
    val home: String?
        get() = when {
            manageable -> "/admin/venues/$id/overview"
            takesOrders -> "/service/venues/$id"
            preparesOrders -> "/kitchen/venues/$id"
            else -> null
        }
    val roleLabel: String get() = roleName
}

fun AccessibleVenue.toView() = VenueView(venueId.value, name, access.roleName, access.permissions)

data class ParentOption(
    val id: UUID?,
    val label: String,
) {
    val value: String get() = id?.toString() ?: ""
}

fun MenuTree.categoryOptions(excluded: Set<CategoryId> = emptySet()): List<ParentOption> {
    fun optionsUnder(parentId: CategoryId?, depth: Int): List<ParentOption> =
        children(parentId)
            .filter { it.id !in excluded }
            .flatMap { listOf(ParentOption(it.id.value, "— ".repeat(depth) + it.name)) + optionsUnder(it.id, depth + 1) }

    return optionsUnder(null, 0)
}

fun MenuTree.parentOptionsFor(id: CategoryId): List<ParentOption> =
    listOf(ParentOption(null, "Top level")) + categoryOptions(excluded = descendantsOf(id) + id)
