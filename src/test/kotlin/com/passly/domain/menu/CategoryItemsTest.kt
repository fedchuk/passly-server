package com.passly.domain.menu

import com.passly.domain.common.Money
import com.passly.domain.common.NotFoundException
import com.passly.domain.common.TaxRate
import com.passly.domain.common.ValidationException
import com.passly.domain.tax.VenueTaxRateId
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CategoryItemsTest {

    private val tenant = TenantId.new()
    private val venue = VenueId.new()
    private val steaks = CategoryItems(venue, CategoryId.new(), emptyList())

    private fun details(name: String, price: String = "10.00") =
        MenuItemDetails(name, description = null, price = Money.of(price), taxRate = TaxRate.of("10"))

    private fun names(items: CategoryItems) = items.items.map { it.name }

    @Test
    fun `new items are appended to the end of the category`() {
        steaks.add(tenant, details("Ribeye"))
        val picanha = steaks.add(tenant, details("Picanha"))

        assertEquals(listOf("Ribeye", "Picanha"), names(steaks))
        assertEquals(1, picanha.sortOrder)
        assertTrue(picanha.available)
        assertTrue(picanha.isNew)
    }

    @Test
    fun `details are validated`() {
        assertEquals("Ribeye", steaks.add(tenant, details("  Ribeye ")).name)
        assertFailsWith<ValidationException> { steaks.add(tenant, details("   ")) }
        assertFailsWith<ValidationException> { steaks.add(tenant, details("Refund", price = "-1.00")) }
    }

    @Test
    fun `blank description is stored as null`() {
        val item = steaks.add(tenant, details("Ribeye").copy(description = "   "))

        assertNull(item.description)
    }

    @Test
    fun `stop list toggles availability`() {
        val ribeye = steaks.add(tenant, details("Ribeye"))

        ribeye.addToStopList()
        assertFalse(ribeye.available)

        ribeye.removeFromStopList()
        assertTrue(ribeye.available)
    }

    @Test
    fun `archiving detaches the item and closes the gap`() {
        val ribeye = steaks.add(tenant, details("Ribeye"))
        val picanha = steaks.add(tenant, details("Picanha"))

        val changed = steaks.archive(ribeye.id)

        assertEquals(setOf(ribeye, picanha), changed)
        assertTrue(ribeye.archived)
        assertNull(ribeye.categoryId)
        assertEquals(0, picanha.sortOrder)
        assertFailsWith<NotFoundException> { steaks[ribeye.id] }
    }

    @Test
    fun `archived item cannot be changed`() {
        val ribeye = steaks.add(tenant, details("Ribeye"))
        steaks.archive(ribeye.id)

        assertFailsWith<ValidationException> { ribeye.update(details("Ribeye 400 g")) }
        assertFailsWith<ValidationException> { ribeye.addToStopList() }
    }

    @Test
    fun `item can be moved to another category`() {
        val fish = CategoryItems(venue, CategoryId.new(), emptyList())
        val salmon = steaks.add(tenant, details("Salmon"))
        val ribeye = steaks.add(tenant, details("Ribeye"))
        fish.add(tenant, details("Sea bass"))

        val changed = steaks.takeOut(salmon.id) + fish.moveIn(salmon)

        assertEquals(listOf("Ribeye"), names(steaks))
        assertEquals(listOf("Sea bass", "Salmon"), names(fish))
        assertEquals(fish.categoryId, salmon.categoryId)
        assertEquals(setOf(ribeye, salmon), changed)
    }

    @Test
    fun `reorder requires exactly the items of the category`() {
        val a = steaks.add(tenant, details("A"))
        val b = steaks.add(tenant, details("B"))

        assertFailsWith<ValidationException> { steaks.reorder(listOf(b.id)) }
        assertFailsWith<ValidationException> { steaks.reorder(listOf(b.id, a.id, a.id)) }

        steaks.reorder(listOf(b.id, a.id))
        assertEquals(listOf("B", "A"), names(steaks))
    }

    @Test
    fun `item goes to its own station or to the station of its category`() {
        val kitchen = StationId.new()
        val grill = StationId.new()
        val tree = MenuTree(venue, emptyList())
        val food = tree.add(tenant, "Food", stationId = kitchen, taxRateId = VenueTaxRateId.new())
        val mains = tree.add(tenant, "Mains", parentId = food.id)
        val mainsItems = CategoryItems(venue, mains.id, emptyList())

        val stew = mainsItems.add(tenant, details("Beef stew"))
        val ribeye = mainsItems.add(tenant, details("Ribeye").copy(stationId = grill))

        assertEquals(kitchen, tree.stationFor(stew))
        assertEquals(grill, tree.stationFor(ribeye))
    }

    @Test
    fun `archived item cannot be moved into a category`() {
        val fish = CategoryItems(venue, CategoryId.new(), emptyList())
        val ribeye = steaks.add(tenant, details("Ribeye"))
        steaks.archive(ribeye.id)

        assertFailsWith<IllegalArgumentException> { fish.moveIn(ribeye) }
        assertTrue(fish.isEmpty())
    }

    @Test
    fun `modifier groups are attached in order without duplicates`() {
        val ribeye = steaks.add(tenant, details("Ribeye"))
        val doneness = ModifierGroupId.new()
        val sauces = ModifierGroupId.new()

        ribeye.attachModifierGroups(listOf(doneness, sauces))

        assertEquals(listOf(doneness, sauces), ribeye.modifierGroupIds)
        assertFailsWith<ValidationException> { ribeye.attachModifierGroups(listOf(doneness, doneness)) }
        assertEquals(listOf(doneness, sauces), ribeye.modifierGroupIds)
    }

    @Test
    fun `archiving detaches modifier groups`() {
        val ribeye = steaks.add(tenant, details("Ribeye"))
        ribeye.attachModifierGroups(listOf(ModifierGroupId.new()))

        steaks.archive(ribeye.id)

        assertTrue(ribeye.modifierGroupIds.isEmpty())
    }

    @Test
    fun `shift moves an item one place and does nothing at the edges`() {
        val a = steaks.add(tenant, details("A"))
        steaks.add(tenant, details("B"))
        val c = steaks.add(tenant, details("C"))

        assertEquals(setOf("C", "B"), steaks.shift(c.id, -1).map { it.name }.toSet())
        assertEquals(listOf("A", "C", "B"), names(steaks))
        assertTrue(steaks.shift(a.id, -1).isEmpty())
    }
}
