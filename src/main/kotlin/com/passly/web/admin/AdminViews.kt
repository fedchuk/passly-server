package com.passly.web.admin

import com.passly.application.access.AccessibleVenue
import com.passly.domain.menu.CategoryId
import com.passly.domain.menu.MenuTree
import com.passly.domain.user.Permission
import com.passly.domain.user.Role
import java.util.*

data class VenueView(
    val id: UUID,
    val name: String,
    val role: Role,
) {
    val manageable: Boolean get() = Permission.EDIT_MENU in role.permissions
    val staffManageable: Boolean get() = Permission.MANAGE_STAFF in role.permissions
    val tablesManageable: Boolean get() = Permission.MANAGE_TABLES in role.permissions
    val takesOrders: Boolean get() = Permission.TAKE_ORDERS in role.permissions
    val closesOrders: Boolean get() = Permission.CLOSE_ORDERS in role.permissions
    val managesStopList: Boolean get() = Permission.MANAGE_STOP_LIST in role.permissions
    val preparesOrders: Boolean get() = Permission.PREPARE_ORDERS in role.permissions
    val settingsManageable: Boolean get() = Permission.MANAGE_VENUE in role.permissions
    val home: String?
        get() = when {
            manageable -> "/admin/venues/$id/overview"
            takesOrders -> "/service/venues/$id"
            preparesOrders -> "/kitchen/venues/$id"
            else -> null
        }
    val roleLabel: String get() = role.name.lowercase().replaceFirstChar { it.uppercase() }
}

fun AccessibleVenue.toView() = VenueView(venueId.value, name, role)

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
