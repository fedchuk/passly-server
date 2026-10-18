package com.passly.application.menu

import com.passly.domain.common.NotFoundException
import com.passly.domain.menu.Category
import com.passly.domain.menu.CategoryId
import com.passly.domain.menu.CategoryRepository
import com.passly.domain.menu.MenuTree
import com.passly.domain.menu.StationId
import com.passly.domain.menu.StationRepository
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.Venue
import com.passly.domain.venue.VenueId
import com.passly.domain.venue.VenueRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CategoryServiceTest {

    private val venue = Venue(VenueId.new(), TenantId.new(), "Bistro")
    private val kitchen = StationId.new()

    private val venues = FakeVenueRepository(venue)
    private val categories = FakeCategoryRepository()
    private val stations = FakeStationRepository(mapOf(kitchen to venue.id))
    private val service = CategoryService(venues, categories, stations)

    @Test
    fun `create stores the new category under the venue's tenant`() {
        val food = service.create(CreateCategoryCommand(venue.id, "Food", stationId = kitchen))

        val stored = categories.stored.getValue(food.id)
        assertEquals(venue.tenantId, stored.tenantId)
        assertEquals(kitchen, stored.stationId)
    }

    @Test
    fun `structural changes lock the venue`() {
        val food = service.create(CreateCategoryCommand(venue.id, "Food"))
        val drinks = service.create(CreateCategoryCommand(venue.id, "Drinks"))
        venues.locks = 0

        service.move(MoveCategoryCommand(venue.id, drinks.id, newParentId = null, position = 0))
        service.shift(venue.id, food.id, -1)
        service.delete(venue.id, food.id)

        assertEquals(3, venues.locks)
    }

    @Test
    fun `move saves only the categories that changed`() {
        val food = service.create(CreateCategoryCommand(venue.id, "Food"))
        service.create(CreateCategoryCommand(venue.id, "Starters", parentId = food.id))
        val wine = service.create(CreateCategoryCommand(venue.id, "Wine", parentId = food.id))
        val drinks = service.create(CreateCategoryCommand(venue.id, "Drinks"))
        categories.lastSaved = emptySet()

        service.move(MoveCategoryCommand(venue.id, wine.id, newParentId = drinks.id))

        assertEquals(setOf(wine.id), categories.lastSaved)
        assertEquals(drinks.id, categories.stored.getValue(wine.id).parentId)
    }

    @Test
    fun `delete removes the category and closes the gap`() {
        val a = service.create(CreateCategoryCommand(venue.id, "A"))
        val b = service.create(CreateCategoryCommand(venue.id, "B"))

        service.delete(venue.id, a.id)

        assertTrue(a.id !in categories.stored)
        assertEquals(0, categories.stored.getValue(b.id).sortOrder)
    }

    @Test
    fun `a station from another venue is treated as not found`() {
        val foreignStation = StationId.new()

        assertFailsWith<NotFoundException> {
            service.create(CreateCategoryCommand(venue.id, "Food", stationId = foreignStation))
        }
    }

    @Test
    fun `unknown venue is not found`() {
        assertFailsWith<NotFoundException> { service.create(CreateCategoryCommand(VenueId.new(), "Food")) }
    }

    private class FakeVenueRepository(private val venue: Venue) : VenueRepository {
        var locks = 0
        override fun findById(id: VenueId) = venue.takeIf { it.id == id }
        override fun findByIdAndLock(id: VenueId) = findById(id)?.also { locks++ }
    }

    private class FakeCategoryRepository : CategoryRepository {
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

    private class FakeStationRepository(private val stationVenues: Map<StationId, VenueId>) : StationRepository {
        override fun existsInVenue(stationId: StationId, venueId: VenueId) = stationVenues[stationId] == venueId
    }
}
