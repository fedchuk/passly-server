package com.passly.application.menu

import com.passly.domain.menu.Category
import com.passly.domain.menu.CategoryId
import com.passly.domain.menu.CategoryItems
import com.passly.domain.menu.CategoryRepository
import com.passly.domain.menu.MenuItem
import com.passly.domain.menu.MenuItemId
import com.passly.domain.menu.MenuItemRepository
import com.passly.domain.menu.MenuTree
import com.passly.domain.menu.ModifierGroup
import com.passly.domain.menu.ModifierGroupId
import com.passly.domain.menu.ModifierGroupRepository
import com.passly.domain.menu.Station
import com.passly.domain.menu.StationId
import com.passly.domain.menu.StationRepository
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.Venue
import com.passly.domain.venue.VenueId
import com.passly.domain.venue.VenueRepository

class FakeVenueRepository(vararg venues: Venue) : VenueRepository {
    private val byId = venues.associateBy { it.id }
    var locks = 0
    override fun findById(id: VenueId) = byId[id]
    override fun findByIdAndLock(id: VenueId) = findById(id)?.also { locks++ }
    override fun findAllByTenant(tenantId: TenantId) = byId.values.filter { it.tenantId == tenantId }
    override fun findAllByIds(ids: Collection<VenueId>) = ids.mapNotNull { byId[it] }
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
    override fun findAllInVenue(venueId: VenueId) =
        stationVenues.filterValues { it == venueId }.keys.map { Station(it, venueId, "Station $it") }
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

    override fun existsWithModifierGroup(groupId: ModifierGroupId) = stored.values.any { groupId in it.modifierGroupIds }

    private fun copyOf(i: MenuItem) = MenuItem.fromStorage(
        i.id, i.tenantId, i.venueId, i.categoryId, i.details, i.sortOrder, i.available, i.archived, i.modifierGroupIds, i.version ?: 0,
    )
}

class FakeModifierGroupRepository : ModifierGroupRepository {
    val stored = linkedMapOf<ModifierGroupId, ModifierGroup>()

    override fun findAll(venueId: VenueId) = stored.values.filter { it.venueId == venueId }.map(::copyOf)

    override fun find(venueId: VenueId, id: ModifierGroupId) = stored[id]?.takeIf { it.venueId == venueId }?.let(::copyOf)

    override fun save(group: ModifierGroup) {
        stored[group.id] = copyOf(group)
    }

    override fun delete(group: ModifierGroup) {
        stored.remove(group.id)
    }

    private fun copyOf(g: ModifierGroup) = ModifierGroup.fromStorage(
        g.id, g.tenantId, g.venueId, g.name, g.minSelect, g.maxSelect, g.modifiers, g.version ?: 0,
    )
}
