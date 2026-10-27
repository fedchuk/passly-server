package com.passly.application.menu

import com.passly.application.tax.FakeTaxRateRepository
import com.passly.application.venue.FakeVenueSettingsRepository
import com.passly.domain.common.ConflictException
import com.passly.domain.common.Money
import com.passly.domain.common.NotFoundException
import com.passly.domain.menu.*
import com.passly.domain.tax.VenueTaxRateId
import com.passly.domain.venue.CountryCode
import com.passly.domain.venue.CurrencyCode
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.Venue
import com.passly.domain.venue.VenueId
import com.passly.domain.venue.VenueSettings
import kotlin.test.*

class MenuItemServiceTest {

    private val venue = Venue(VenueId.new(), TenantId.new(), "Bistro")
    private val grill = StationId.new()

    private val venues = FakeVenueRepository(venue)
    private val categories = FakeCategoryRepository()
    private val stations = FakeStationRepository(mapOf(grill to venue.id))
    private val items = FakeMenuItemRepository()
    private val modifierGroups = FakeModifierGroupRepository()
    private val menuChanges = FakeMenuChangeRecorder()
    private val taxRates = FakeTaxRateRepository()
    private val categoryService = CategoryService(
        venues, categories, stations, items, menuChanges, taxRates,
        FakeVenueSettingsRepository(VenueSettings.fromStorage(venue.id, CountryCode.of("FR"), CurrencyCode.of("EUR"), version = 0)),
    )
    private val service = MenuItemService(venues, categories, stations, items, modifierGroups, menuChanges, taxRates)

    private val steaks = categoryService.create(CreateCategoryCommand(venue.id, "Steaks", stationId = grill)).id
    private val fish = categoryService.create(CreateCategoryCommand(venue.id, "Fish", stationId = grill)).id

    private fun details(name: String, price: String = "20.00", station: StationId? = null) =
        MenuItemDetails(name, null, Money.of(price), station)

    private fun create(category: CategoryId, name: String) =
        service.create(CreateMenuItemCommand(venue.id, category, details(name)))

    private fun namesIn(category: CategoryId) = service.listInCategory(venue.id, category).map { it.name }

    @Test
    fun `created items are listed in order under the venue's tenant`() {
        create(steaks, "Ribeye")
        val picanha = create(steaks, "Picanha")

        assertEquals(listOf("Ribeye", "Picanha"), namesIn(steaks))
        assertEquals(venue.tenantId, items.stored.getValue(picanha.id).tenantId)
    }

    @Test
    fun `item cannot be created in an unknown category or with a foreign station`() {
        assertFailsWith<NotFoundException> { create(CategoryId.new(), "Ghost") }
        assertFailsWith<NotFoundException> {
            service.create(CreateMenuItemCommand(venue.id, steaks, details("Ribeye", station = StationId.new())))
        }
    }

    @Test
    fun `update changes details and rejects an outdated version`() {
        val ribeye = create(steaks, "Ribeye")

        service.update(UpdateMenuItemCommand(venue.id, ribeye.id, details("Ribeye 300 g", "27.50", grill), 0))

        val stored = items.stored.getValue(ribeye.id)
        assertEquals("Ribeye 300 g", stored.name)
        assertEquals(Money.of("27.50"), stored.price)
        assertEquals(grill, stored.stationId)
        assertFailsWith<ConflictException> {
            service.update(UpdateMenuItemCommand(venue.id, ribeye.id, details("Ribeye"), expectedVersion = 5))
        }
    }

    @Test
    fun `availability toggles the stop list`() {
        val ribeye = create(steaks, "Ribeye")

        service.changeAvailability(venue.id, ribeye.id, available = false)
        assertFalse(items.stored.getValue(ribeye.id).available)

        service.changeAvailability(venue.id, ribeye.id, available = true)
        assertTrue(items.stored.getValue(ribeye.id).available)
    }

    @Test
    fun `every change is recorded for the waiter apps`() {
        val ribeye = create(steaks, "Ribeye")
        menuChanges.recorded.clear()

        service.changeAvailability(venue.id, ribeye.id, available = false)

        assertEquals(listOf(venue.id to MenuChangeKind.STOP_LIST), menuChanges.recorded)
    }

    @Test
    fun `move puts the item at the end of the target category`() {
        val salmon = create(steaks, "Salmon")
        create(steaks, "Ribeye")
        create(fish, "Sea bass")

        service.move(MoveMenuItemCommand(venue.id, salmon.id, fish))

        assertEquals(listOf("Ribeye"), namesIn(steaks))
        assertEquals(listOf("Sea bass", "Salmon"), namesIn(fish))
    }

    @Test
    fun `move to the same category changes nothing`() {
        val ribeye = create(steaks, "Ribeye")
        items.lastSaved = emptySet()

        service.move(MoveMenuItemCommand(venue.id, ribeye.id, steaks))

        assertTrue(items.lastSaved.isEmpty())
    }

    @Test
    fun `reorder sets the exact order`() {
        val a = create(steaks, "A")
        val b = create(steaks, "B")

        service.reorder(ReorderMenuItemsCommand(venue.id, steaks, listOf(b.id, a.id)))

        assertEquals(listOf("B", "A"), namesIn(steaks))
    }

    @Test
    fun `archived item disappears and lets the category be deleted`() {
        val ribeye = create(steaks, "Ribeye")

        service.archive(venue.id, ribeye.id)

        assertTrue(items.stored.getValue(ribeye.id).archived)
        assertNull(items.stored.getValue(ribeye.id).categoryId)
        assertFailsWith<NotFoundException> { service.get(venue.id, ribeye.id) }
        categoryService.delete(venue.id, steaks)
        assertTrue(steaks !in categories.stored)
    }

    @Test
    fun `modifier groups of the venue are attached in the given order`() {
        val ribeye = create(steaks, "Ribeye")
        val doneness = modifierGroup("Doneness")
        val sauces = modifierGroup("Sauces")

        service.attachModifierGroups(AttachModifierGroupsCommand(venue.id, ribeye.id, listOf(sauces, doneness), 0))

        assertEquals(listOf(sauces, doneness), items.stored.getValue(ribeye.id).modifierGroupIds)
    }

    @Test
    fun `modifier group of another venue cannot be attached`() {
        val ribeye = create(steaks, "Ribeye")
        val foreign = ModifierGroup.create(TenantId.new(), VenueId.new(), "Foreign", 0, 1, listOf(
            ModifierDraft(
                "X",
                Money.of("0")
            )
        ))
        modifierGroups.save(foreign)

        assertFailsWith<NotFoundException> {
            service.attachModifierGroups(AttachModifierGroupsCommand(venue.id, ribeye.id, listOf(foreign.id)))
        }
        assertTrue(items.stored.getValue(ribeye.id).modifierGroupIds.isEmpty())
    }

    @Test
    fun `shift moves the item within its category and locks the venue`() {
        create(steaks, "Ribeye")
        val picanha = create(steaks, "Picanha")
        venues.locks = 0

        service.shift(venue.id, picanha.id, -1)

        assertEquals(listOf("Picanha", "Ribeye"), namesIn(steaks))
        assertEquals(1, venues.locks)
    }

    private fun modifierGroup(name: String): ModifierGroupId {
        val group = ModifierGroup.create(venue.tenantId, venue.id, name, 0, 1, listOf(ModifierDraft("Option", Money.of("0"))))
        modifierGroups.save(group)
        return group.id
    }

    @Test
    fun `item keeps its own rate and a rate of another venue is not found`() {
        val alcohol = taxRates.load(venue.id).rates.single { it.name == "Alcohol" }

        val rioja = service.create(CreateMenuItemCommand(venue.id, steaks, details("Rioja").copy(taxRateId = alcohol.id)))

        assertEquals(alcohol.id, items.stored.getValue(rioja.id).taxRateId)
        assertFailsWith<NotFoundException> {
            service.create(CreateMenuItemCommand(venue.id, steaks, details("Ghost").copy(taxRateId = VenueTaxRateId.new())))
        }
    }

    @Test
    fun `archived item lets go of its own rate`() {
        val alcohol = taxRates.load(venue.id).rates.single { it.name == "Alcohol" }
        val rioja = service.create(CreateMenuItemCommand(venue.id, steaks, details("Rioja").copy(taxRateId = alcohol.id)))

        service.archive(venue.id, rioja.id)

        assertEquals(null, items.stored.getValue(rioja.id).taxRateId)
        assertFalse(items.existsWithTaxRate(alcohol.id))
    }
}
