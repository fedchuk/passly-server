package com.passly.domain.menu

import com.passly.domain.common.NotFoundException
import com.passly.domain.common.ValidationException
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId

class CategoryItems {
    val venueId: VenueId
    val categoryId: CategoryId

    constructor(venueId: VenueId, categoryId: CategoryId, items: Collection<MenuItem>) {
        this.venueId = venueId
        this.categoryId = categoryId
        this.byId = items.associateByTo(LinkedHashMap()) { it.id }
        require(items.all { it.categoryId == categoryId && !it.archived }) {
            "CategoryItems accepts only active items of category $categoryId"
        }
    }

    private val byId: MutableMap<MenuItemId, MenuItem>

    val items: List<MenuItem> get() = byId.values.sortedBy { it.sortOrder }

    operator fun get(id: MenuItemId): MenuItem = byId[id] ?: throw NotFoundException("Menu item", id)

    operator fun contains(id: MenuItemId): Boolean = id in byId

    fun isEmpty(): Boolean = byId.isEmpty()

    fun add(tenantId: TenantId, details: MenuItemDetails): MenuItem {
        val item = MenuItem.new(tenantId, venueId, categoryId, details, nextPosition())
        byId[item.id] = item
        return item
    }

    fun moveIn(item: MenuItem): MenuItem {
        require(item.venueId == venueId && !item.archived) { "Item $item cannot be moved into $categoryId" }
        item.categoryId = categoryId
        item.sortOrder = nextPosition()
        byId[item.id] = item
        return item
    }

    fun takeOut(id: MenuItemId): Set<MenuItem> {
        get(id)
        byId.remove(id)
        return assignPositions(items)
    }

    fun archive(id: MenuItemId): Set<MenuItem> {
        val item = get(id)
        item.archive()
        byId.remove(id)
        return assignPositions(items) + item
    }

    fun reorder(orderedIds: List<MenuItemId>): Set<MenuItem> {
        if (orderedIds.toSet().size != orderedIds.size) {
            throw ValidationException("orderedIds contains duplicates")
        }
        if (byId.keys != orderedIds.toSet()) {
            throw ValidationException("orderedIds must list exactly the items of the category")
        }
        return assignPositions(orderedIds.map { get(it) })
    }

    private fun nextPosition(): Int = (byId.values.maxOfOrNull { it.sortOrder } ?: -1) + 1

    private fun assignPositions(ordered: List<MenuItem>): Set<MenuItem> {
        val changed = linkedSetOf<MenuItem>()
        ordered.forEachIndexed { index, item ->
            if (item.sortOrder != index) {
                item.sortOrder = index
                changed += item
            }
        }
        return changed
    }
}
