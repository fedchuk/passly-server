package com.passly.application.menu

import com.passly.domain.menu.Category
import com.passly.domain.menu.CategoryId
import com.passly.domain.menu.CategoryItems
import com.passly.domain.menu.CategoryRepository
import com.passly.domain.menu.MenuItem
import com.passly.domain.menu.MenuItemId
import com.passly.domain.menu.MenuItemRepository
import com.passly.domain.menu.MenuTree
import com.passly.domain.menu.StationId
import com.passly.domain.menu.StationRepository
import com.passly.domain.venue.Venue
import com.passly.domain.venue.VenueId
import com.passly.domain.venue.VenueRepository

class FakeVenueRepository(private val venue: Venue) : VenueRepository {
    var locks = 0
    override fun findById(id: VenueId) = venue.takeIf { it.id == id }
    override fun findByIdAndLock(id: VenueId) = findById(id)?.also { locks++ }
}

class FakeCategoryRepository : CategoryRepository {
    val stored = linkedMapOf<CategoryId, Category>()
    var lastSaved: Set<CategoryId> = emptySet()

    override fun loadTree(venueId: VenueId) =
        MenuTree(venueId, stored.values.filter { it.venueId == venueId }.map(::copyOf))

    override fun saveAll(categories: Collection<Category>) {
        categories.forEach { stored[it.id] = copyOf(it) }
        lastSaved = categories.map { it.id }.toSet()
    }

    override fun delete(category: Category) {
        stored.remove(category.id)
    }

    private fun copyOf(c: Category) = Category.fromStorage(
        c.id, c.tenantId, c.venueId, c.parentId, c.name, c.sortOrder, c.stationId, c.active, c.version ?: 0,
    )
}

class FakeStationRepository(private val stationVenues: Map<StationId, VenueId>) : StationRepository {
    override fun existsInVenue(stationId: StationId, venueId: VenueId) = stationVenues[stationId] == venueId
}

class FakeMenuItemRepository : MenuItemRepository {
    val stored = linkedMapOf<MenuItemId, MenuItem>()
    var lastSaved: Set<MenuItemId> = emptySet()

    override fun loadCategoryItems(venueId: VenueId, categoryId: CategoryId) = CategoryItems(
        venueId,
        categoryId,
        stored.values.filter { it.venueId == venueId && it.categoryId == categoryId && !it.archived }.map(::copyOf),
    )

    override fun findActive(venueId: VenueId, id: MenuItemId) =
        stored[id]?.takeIf { it.venueId == venueId && !it.archived }?.let(::copyOf)

    override fun existsInCategory(categoryId: CategoryId) = stored.values.any { it.categoryId == categoryId }

    override fun saveAll(items: Collection<MenuItem>) {
        items.forEach { stored[it.id] = copyOf(it) }
        lastSaved = items.map { it.id }.toSet()
    }

    private fun copyOf(i: MenuItem) = MenuItem.fromStorage(
        i.id, i.tenantId, i.venueId, i.categoryId, i.details, i.sortOrder, i.available, i.archived, i.version ?: 0,
    )
}
