package com.passly.web.admin

import com.passly.application.access.AccessibleVenue
import com.passly.domain.menu.CategoryId
import com.passly.domain.menu.MenuTree
import com.passly.domain.menu.StationId
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
    val preparesOrders: Boolean get() = Permission.PREPARE_ORDERS in role.permissions
    val home: String?
        get() = when {
            manageable -> "/admin/venues/$id/overview"
            takesOrders -> "/service/venues/$id"
            preparesOrders -> "/kitchen/venues/$id"
            else -> null
        }
    val roleLabel: String get() = role.name.lowercase().replaceFirstChar { it.uppercase() }
}

data class CategoryNodeView(
    val id: UUID,
    val name: String,
    val station: String?,
    val active: Boolean,
    val first: Boolean,
    val last: Boolean,
    val children: List<CategoryNodeView>,
)

fun AccessibleVenue.toView() = VenueView(venueId.value, name, role)

fun MenuTree.toNodeViews(stationNames: Map<StationId, String>, parentId: CategoryId? = null): List<CategoryNodeView> {
    val siblings = children(parentId)
    return siblings.mapIndexed { index, category ->
        CategoryNodeView(
            id = category.id.value,
            name = category.name,
            station = category.stationId?.let { stationNames[it] },
            active = category.active,
            first = index == 0,
            last = index == siblings.lastIndex,
            children = toNodeViews(stationNames, category.id),
        )
    }
}

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
