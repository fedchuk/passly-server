package com.passly.application.domain.menu

import com.passly.domain.common.ConflictException
import com.passly.domain.common.NotFoundException
import com.passly.domain.common.ValidationException
import com.passly.domain.menu.Category
import com.passly.domain.menu.CategoryId
import com.passly.domain.menu.MenuTree
import com.passly.domain.menu.StationId
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId
import org.hibernate.internal.util.collections.CollectionHelper.setOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MenuTreeTest {

    private val tenant = TenantId.new()
    private val tree = MenuTree(VenueId.new(), emptyList())

    private fun namesUnder(parent: Category?) = tree.children(parent?.id).map { it.name }

    @Test
    fun `new categories are appended after their siblings`() {
        val food = tree.add(tenant, "Food")
        val drinks = tree.add(tenant, "Drinks")
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
        assertEquals("Wine", tree.add(tenant, "  Wine ").name)
        assertFailsWith<ValidationException> { tree.add(tenant, "   ") }
        assertFailsWith<ValidationException> { tree.add(tenant, "x".repeat(Category.MAX_NAME_LENGTH + 1)) }
    }

    @Test
    fun `tree can be nested to any depth`() {
        var parent: Category? = null
        repeat(50) { level -> parent = tree.add(tenant, "Level $level", parentId = parent?.id) }

        val root = tree.children(null).single()
        assertEquals(49, tree.descendantsOf(root.id).size)
    }

    @Test
    fun `station is inherited from the nearest ancestor that has one`() {
        val kitchen = StationId.new()
        val grill = StationId.new()
        val food = tree.add(tenant, "Food", stationId = kitchen)
        val mains = tree.add(tenant, "Mains", parentId = food.id)
        val steaks = tree.add(tenant, "Steaks", parentId = mains.id, stationId = grill)
        val ribeye = tree.add(tenant, "Ribeye cuts", parentId = steaks.id)
        val desserts = tree.add(tenant, "Desserts")

        assertEquals(kitchen, tree.effectiveStation(mains.id))
        assertEquals(grill, tree.effectiveStation(ribeye.id))
        assertNull(tree.effectiveStation(desserts.id))
    }

    @Test
    fun `moving a category into itself or its subtree is rejected`() {
        val food = tree.add(tenant, "Food")
        val mains = tree.add(tenant, "Mains", parentId = food.id)
        val steaks = tree.add(tenant, "Steaks", parentId = mains.id)

        assertFailsWith<ConflictException> { tree.move(food.id, newParentId = food.id) }
        assertFailsWith<ConflictException> { tree.move(food.id, newParentId = steaks.id) }
        assertEquals(food.id, mains.parentId)
    }

    @Test
    fun `moving to another parent renumbers both sibling lists`() {
        val food = tree.add(tenant, "Food")
        val drinks = tree.add(tenant, "Drinks")
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
    fun `a category can be moved to the top level`() {
        val food = tree.add(tenant, "Food")
        val desserts = tree.add(tenant, "Desserts", parentId = food.id)

        tree.move(desserts.id, newParentId = null, position = 0)

        assertEquals(listOf("Desserts", "Food"), namesUnder(null))
        assertNull(desserts.parentId)
    }

    @Test
    fun `shift moves one place and does nothing at the edges`() {
        val a = tree.add(tenant, "A")
        tree.add(tenant, "B")
        val c = tree.add(tenant, "C")

        assertEquals(emptySet(), tree.shift(a.id, -1))
        assertEquals(emptySet(), tree.shift(c.id, +1))

        tree.shift(c.id, -1)
        assertEquals(listOf("A", "C", "B"), namesUnder(null))
    }

    @Test
    fun `reorder requires exactly the current children`() {
        val a = tree.add(tenant, "A")
        val b = tree.add(tenant, "B")
        val c = tree.add(tenant, "C")

        assertFailsWith<ValidationException> { tree.reorder(null, listOf(c.id, a.id)) }
        assertFailsWith<ValidationException> { tree.reorder(null, listOf(c.id, a.id, a.id)) }

        tree.reorder(null, listOf(c.id, a.id, b.id))
        assertEquals(listOf("C", "A", "B"), namesUnder(null))
    }

    @Test
    fun `removeLeaf closes the gap among siblings and rejects categories with children`() {
        val food = tree.add(tenant, "Food")
        tree.add(tenant, "Starters", parentId = food.id)
        val drinks = tree.add(tenant, "Drinks")
        val desserts = tree.add(tenant, "Desserts")

        assertFailsWith<ConflictException> { tree.removeLeaf(food.id) }

        val changed = tree.removeLeaf(drinks.id)
        assertEquals(listOf("Food", "Desserts"), namesUnder(null))
        assertEquals(setOf(desserts), changed)
        assertTrue(drinks.id !in tree)
    }
}
