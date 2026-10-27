package com.passly.domain.menu

import com.passly.domain.common.ConflictException
import com.passly.domain.common.NotFoundException
import com.passly.domain.common.ValidationException
import com.passly.domain.tax.VenueTaxRateId
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId
import kotlin.test.*

class MenuTreeTest {

    private val tenant = TenantId.new()
    private val kitchen = StationId.new()
    private val food10 = VenueTaxRateId.new()
    private val tree = MenuTree(VenueId.new(), emptyList())

    private fun namesUnder(parent: Category?) = tree.children(parent?.id).map { it.name }

    private fun root(name: String, station: StationId = kitchen) = tree.add(tenant, name, stationId = station, taxRateId = food10)

    @Test
    fun `new categories are appended after their siblings`() {
        val food = root("Food")
        val drinks = root("Drinks")
        tree.add(tenant, "Starters", parentId = food.id)
        tree.add(tenant, "Mains", parentId = food.id)

        assertEquals(listOf("Food", "Drinks"), namesUnder(null))
        assertEquals(listOf("Starters", "Mains"), namesUnder(food))
        assertEquals(1, drinks.sortOrder)
        assertTrue(drinks.isNew)
    }

    @Test
    fun `adding under an unknown parent fails`() {
        assertFailsWith<NotFoundException> { tree.add(tenant, "Orphan", parentId = CategoryId.new()) }
    }

    @Test
    fun `name is trimmed and must not be blank`() {
        assertEquals("Wine", root("  Wine ").name)
        assertFailsWith<ValidationException> { root("   ") }
        assertFailsWith<ValidationException> { root("x".repeat(Category.MAX_NAME_LENGTH + 1)) }
    }

    @Test
    fun `a top-level category needs a station and a subcategory can inherit it`() {
        val error = assertFailsWith<ValidationException> { tree.add(tenant, "Food") }
        assertEquals("stationId", error.field)
        assertTrue(tree.categories.isEmpty())

        val food = root("Food")
        val mains = tree.add(tenant, "Mains", parentId = food.id)
        assertNull(mains.stationId)
        assertEquals(kitchen, tree.effectiveStation(mains.id))
    }

    @Test
    fun `a top-level category cannot lose its station but a subcategory can`() {
        val food = root("Food")
        val mains = tree.add(tenant, "Mains", parentId = food.id, stationId = StationId.new())

        assertFailsWith<ValidationException> { food.assignStation(null) }
        assertEquals(kitchen, food.stationId)

        mains.assignStation(null)
        assertNull(mains.stationId)
    }

    @Test
    fun `tree can be nested to any depth`() {
        var parent: Category? = null
        repeat(50) { level ->
            parent = tree.add(
                tenant, "Level $level", parentId = parent?.id,
                stationId = if (parent == null) kitchen else null,
                taxRateId = if (parent == null) food10 else null,
            )
        }

        val root = tree.children(null).single()
        assertEquals(49, tree.descendantsOf(root.id).size)
    }

    @Test
    fun `station is inherited from the nearest ancestor that has one`() {
        val grill = StationId.new()
        val pastry = StationId.new()
        val food = root("Food")
        val mains = tree.add(tenant, "Mains", parentId = food.id)
        val steaks = tree.add(tenant, "Steaks", parentId = mains.id, stationId = grill)
        val ribeye = tree.add(tenant, "Ribeye cuts", parentId = steaks.id)
        val desserts = root("Desserts", pastry)

        assertEquals(kitchen, tree.effectiveStation(mains.id))
        assertEquals(grill, tree.effectiveStation(ribeye.id))
        assertEquals(pastry, tree.effectiveStation(desserts.id))
    }

    @Test
    fun `moving a category into itself or its subtree is rejected`() {
        val food = root("Food")
        val mains = tree.add(tenant, "Mains", parentId = food.id)
        val steaks = tree.add(tenant, "Steaks", parentId = mains.id)

        assertFailsWith<ConflictException> { tree.move(food.id, newParentId = food.id) }
        assertFailsWith<ConflictException> { tree.move(food.id, newParentId = steaks.id) }
        assertEquals(food.id, mains.parentId)
    }

    @Test
    fun `moving to another parent renumbers both sibling lists`() {
        val food = root("Food")
        val drinks = root("Drinks")
        val starters = tree.add(tenant, "Starters", parentId = food.id)
        val wine = tree.add(tenant, "Wine", parentId = food.id)
        val salads = tree.add(tenant, "Salads", parentId = food.id)
        val beer = tree.add(tenant, "Beer", parentId = drinks.id)

        val changed = tree.move(wine.id, newParentId = drinks.id, position = 0)

        assertEquals(listOf("Starters", "Salads"), namesUnder(food))
        assertEquals(listOf("Wine", "Beer"), namesUnder(drinks))
        assertEquals(setOf(wine, salads, beer), changed)
        assertEquals(0, starters.sortOrder)
    }

    @Test
    fun `a category with its own station can be moved to the top level`() {
        val food = root("Food")
        val desserts = tree.add(tenant, "Desserts", parentId = food.id, stationId = StationId.new())

        tree.move(desserts.id, newParentId = null, position = 0)

        assertEquals(listOf("Desserts", "Food"), namesUnder(null))
        assertNull(desserts.parentId)
    }

    @Test
    fun `a category that inherits its station cannot become top-level`() {
        val food = root("Food")
        val desserts = tree.add(tenant, "Desserts", parentId = food.id)

        assertFailsWith<ConflictException> { tree.move(desserts.id, newParentId = null) }
        assertEquals(food.id, desserts.parentId)
    }

    @Test
    fun `shift moves one place and does nothing at the edges`() {
        val a = root("A")
        root("B")
        val c = root("C")

        assertEquals(emptySet(), tree.shift(a.id, -1))
        assertEquals(emptySet(), tree.shift(c.id, +1))

        tree.shift(c.id, -1)
        assertEquals(listOf("A", "C", "B"), namesUnder(null))
    }

    @Test
    fun `reorder requires exactly the current children`() {
        val a = root("A")
        val b = root("B")
        val c = root("C")

        assertFailsWith<ValidationException> { tree.reorder(null, listOf(c.id, a.id)) }
        assertFailsWith<ValidationException> { tree.reorder(null, listOf(c.id, a.id, a.id)) }

        tree.reorder(null, listOf(c.id, a.id, b.id))
        assertEquals(listOf("C", "A", "B"), namesUnder(null))
    }

    @Test
    fun `removeLeaf closes the gap among siblings and rejects categories with children`() {
        val food = root("Food")
        tree.add(tenant, "Starters", parentId = food.id)
        val drinks = root("Drinks")
        val desserts = root("Desserts")

        assertFailsWith<ConflictException> { tree.removeLeaf(food.id) }

        val changed = tree.removeLeaf(drinks.id)
        assertEquals(listOf("Food", "Desserts"), namesUnder(null))
        assertEquals(setOf(desserts), changed)
        assertTrue(drinks.id !in tree)
    }

    @Test
    fun `a top-level category needs a tax rate and a subcategory inherits it`() {
        val error = assertFailsWith<ValidationException> { tree.add(tenant, "Food", stationId = kitchen) }
        assertEquals("taxRateId", error.field)

        val food = root("Food")
        val wine = tree.add(tenant, "Wine", parentId = food.id)
        val reds = tree.add(tenant, "Reds", parentId = wine.id)
        assertNull(wine.taxRateId)
        assertEquals(food10, tree.effectiveTaxRate(reds.id))

        val alcohol = VenueTaxRateId.new()
        wine.assignTaxRate(alcohol)
        assertEquals(alcohol, tree.effectiveTaxRate(reds.id))
        assertFailsWith<ValidationException> { food.assignTaxRate(null) }
    }

    @Test
    fun `subcategory moved to the top level keeps the rate it had`() {
        val food = root("Food")
        val wine = tree.add(tenant, "Wine", parentId = food.id, stationId = StationId.new())

        tree.move(wine.id, null)

        assertEquals(food10, wine.taxRateId)
    }
}
