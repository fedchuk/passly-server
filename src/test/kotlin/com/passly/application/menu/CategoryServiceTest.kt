package com.passly.application.menu

import com.passly.domain.common.*
import com.passly.domain.menu.MenuItemDetails
import com.passly.domain.menu.StationId
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.Venue
import com.passly.domain.venue.VenueId
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
    private val items = FakeMenuItemRepository()
    private val menuChanges = FakeMenuChangeRecorder()
    private val service = CategoryService(venues, categories, stations, items, menuChanges)

    private fun root(name: String) = service.create(CreateCategoryCommand(venue.id, name, stationId = kitchen))

    @Test
    fun `create stores the new category under the venue's tenant`() {
        val food = service.create(CreateCategoryCommand(venue.id, "Food", stationId = kitchen))

        val stored = categories.stored.getValue(food.id)
        assertEquals(venue.tenantId, stored.tenantId)
        assertEquals(kitchen, stored.stationId)
    }

    @Test
    fun `structural changes lock the venue`() {
        val food = root("Food")
        val drinks = root("Drinks")
        venues.locks = 0

        service.move(MoveCategoryCommand(venue.id, drinks.id, newParentId = null, position = 0))
        service.shift(venue.id, food.id, -1)
        service.delete(venue.id, food.id)

        assertEquals(3, venues.locks)
    }

    @Test
    fun `move saves only the categories that changed`() {
        val food = root("Food")
        service.create(CreateCategoryCommand(venue.id, "Starters", parentId = food.id))
        val wine = service.create(CreateCategoryCommand(venue.id, "Wine", parentId = food.id))
        val drinks = root("Drinks")
        categories.lastSaved = emptySet()

        service.move(MoveCategoryCommand(venue.id, wine.id, newParentId = drinks.id))

        assertEquals(setOf(wine.id), categories.lastSaved)
        assertEquals(drinks.id, categories.stored.getValue(wine.id).parentId)
    }

    @Test
    fun `delete removes the category and closes the gap`() {
        val a = root("A")
        val b = root("B")

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

    @Test
    fun `update with an outdated version is rejected`() {
        val food = root("Food")

        assertFailsWith<ConflictException> {
            service.update(UpdateCategoryCommand(venue.id, food.id, "Kitchen", null, true, expectedVersion = 7))
        }
        assertEquals("Food", categories.stored.getValue(food.id).name)
    }

    @Test
    fun `category with menu items cannot be deleted`() {
        val steaks = root("Steaks")
        val ribeye = items.loadCategoryItems(venue.id, steaks.id).add(
            venue.tenantId,
            MenuItemDetails("Ribeye", null, Money.of("26.00"), TaxRate.of("10")),
        )
        items.saveAll(listOf(ribeye))

        assertFailsWith<ConflictException> { service.delete(venue.id, steaks.id) }
        assertTrue(steaks.id in categories.stored)
    }

    @Test
    fun `a top-level category is rejected without a station and keeps it on update`() {
        assertFailsWith<ValidationException> { service.create(CreateCategoryCommand(venue.id, "Food")) }
        val food = root("Food")

        assertFailsWith<ValidationException> {
            service.update(UpdateCategoryCommand(venue.id, food.id, "Food", null, true))
        }
        assertEquals(kitchen, categories.stored.getValue(food.id).stationId)
    }
}
