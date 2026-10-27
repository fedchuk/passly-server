package com.passly.application.menu

import com.passly.domain.common.ConflictException
import com.passly.domain.common.Money
import com.passly.domain.common.NotFoundException
import com.passly.domain.menu.*
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.Venue
import com.passly.domain.venue.VenueId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ModifierGroupServiceTest {

    private val venue = Venue(VenueId.new(), TenantId.new(), "Bistro")
    private val groups = FakeModifierGroupRepository()
    private val items = FakeMenuItemRepository()
    private val menuChanges = FakeMenuChangeRecorder()
    private val service = ModifierGroupService(FakeVenueRepository(venue), groups, items, menuChanges)

    private fun option(name: String, delta: String = "0") = ModifierDraft(name, Money.of(delta))

    private fun create(name: String, min: Int = 1, max: Int = 1) =
        service.create(CreateModifierGroupCommand(venue.id, name, min, max, listOf(option("A"), option("B"))))

    @Test
    fun `groups are created for the venue's tenant and listed by name`() {
        create("Size")
        val doneness = create("doneness")

        assertEquals(listOf("doneness", "Size"), service.list(venue.id).map { it.name })
        assertEquals(venue.tenantId, groups.stored.getValue(doneness.id).tenantId)
    }

    @Test
    fun `update replaces name, limits and options at once`() {
        val extras = create("Extras", min = 0, max = 1)
        val kept = extras.modifiers.first()

        service.update(
            UpdateModifierGroupCommand(
                venue.id, extras.id, "Toppings", 0, 3,
                listOf(ModifierDraft(kept.name, Money.of("0.50"), kept.id), option("C", "1.00")),
                expectedVersion = 0,
            ),
        )

        val stored = groups.stored.getValue(extras.id)
        assertEquals("Toppings", stored.name)
        assertEquals(3, stored.maxSelect)
        assertEquals(kept.id, stored.modifiers.first().id)
        assertEquals(Money.of("0.50"), stored.modifiers.first().priceDelta)
    }

    @Test
    fun `update with an outdated version is rejected`() {
        val size = create("Size")

        assertFailsWith<ConflictException> {
            service.update(UpdateModifierGroupCommand(venue.id, size.id, "Sizes", 1, 1, listOf(option("A")), expectedVersion = 3))
        }
        assertEquals("Size", groups.stored.getValue(size.id).name)
    }

    @Test
    fun `group of another venue is not found`() {
        val size = create("Size")

        assertFailsWith<NotFoundException> { service.get(VenueId.new(), size.id) }
        assertFailsWith<NotFoundException> { service.get(venue.id, ModifierGroupId.new()) }
    }

    @Test
    fun `deleted group is gone`() {
        val size = create("Size")

        service.delete(venue.id, size.id)

        assertTrue(size.id !in groups.stored)
    }

    @Test
    fun `group used by a menu item cannot be deleted`() {
        val size = create("Size")
        val category = CategoryItems(venue.id, CategoryId.new(), emptyList())
        val latte = category.add(venue.tenantId, MenuItemDetails("Latte", null, Money.of("3.00")))
        latte.attachModifierGroups(listOf(size.id))
        items.saveAll(listOf(latte))

        assertFailsWith<ConflictException> { service.delete(venue.id, size.id) }
        assertTrue(size.id in groups.stored)
    }
}
