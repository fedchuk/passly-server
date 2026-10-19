package com.passly.domain.menu

import com.passly.domain.common.Money
import com.passly.domain.common.ValidationException
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ModifierGroupTest {

    private val tenant = TenantId.new()
    private val venue = VenueId.new()

    private fun option(name: String, delta: String = "0", id: ModifierId? = null) =
        ModifierDraft(name, Money.of(delta), id)

    private fun doneness() = ModifierGroup.create(
        tenant, venue, "Doneness", minSelect = 1, maxSelect = 1,
        drafts = listOf(option("Rare"), option("Medium"), option("Well done")),
    )

    private fun extras() = ModifierGroup.create(
        tenant, venue, "Extras", minSelect = 0, maxSelect = 2,
        drafts = listOf(option("Cheese", "1.00"), option("Bacon", "1.50"), option("No onion", "-0.20")),
    )

    @Test
    fun `group keeps options in the given order`() {
        val group = extras()

        assertEquals(listOf("Cheese", "Bacon", "No onion"), group.modifiers.map { it.name })
        assertEquals(Money.of("-0.20"), group.modifiers.last().priceDelta)
        assertTrue(group.isNew)
    }

    @Test
    fun `limits must be consistent`() {
        val options = listOf(option("Rare"), option("Medium"))

        assertFailsWith<ValidationException> { ModifierGroup.create(tenant, venue, "X", -1, 1, options) }
        assertFailsWith<ValidationException> { ModifierGroup.create(tenant, venue, "X", 0, 0, options) }
        assertFailsWith<ValidationException> { ModifierGroup.create(tenant, venue, "X", 2, 1, options) }
        assertFailsWith<ValidationException> { ModifierGroup.create(tenant, venue, "X", 3, 3, options) }
    }

    @Test
    fun `group needs at least one option with a unique name`() {
        assertFailsWith<ValidationException> { ModifierGroup.create(tenant, venue, "Empty", 0, 1, emptyList()) }
        assertFailsWith<ValidationException> {
            ModifierGroup.create(tenant, venue, "Size", 1, 1, listOf(option("Large"), option(" large ")))
        }
    }

    @Test
    fun `replacing options keeps the ids of kept options`() {
        val group = extras()
        val (cheese, _, noOnion) = group.modifiers

        group.replaceModifiers(
            listOf(option("Bacon & egg", "2.00"), option("No onion", "-0.20", noOnion.id), option("Cheese", "1.20", cheese.id)),
        )

        assertEquals(listOf("Bacon & egg", "No onion", "Cheese"), group.modifiers.map { it.name })
        assertEquals(noOnion.id, group.modifiers[1].id)
        assertEquals(Money.of("1.20"), group.modifiers[2].priceDelta)
    }

    @Test
    fun `replacing with an unknown or duplicated id is rejected`() {
        val group = extras()
        val cheese = group.modifiers.first()

        assertFailsWith<ValidationException> { group.replaceModifiers(listOf(option("Ghost", id = ModifierId.new()))) }
        assertFailsWith<ValidationException> {
            group.replaceModifiers(listOf(option("Cheese", id = cheese.id), option("Cheddar", id = cheese.id)))
        }
    }

    @Test
    fun `options cannot be removed below the minimum selection`() {
        val group = ModifierGroup.create(tenant, venue, "Sauces", 2, 3, listOf(option("BBQ"), option("Mayo"), option("Aioli")))

        assertFailsWith<ValidationException> { group.replaceModifiers(listOf(option("BBQ", id = group.modifiers[0].id))) }
        assertEquals(3, group.modifiers.size)
    }

    @Test
    fun `selection must respect the limits`() {
        val group = doneness()
        val (rare, medium) = group.modifiers

        assertEquals(listOf(medium), group.select(listOf(medium.id)))
        assertFailsWith<ValidationException> { group.select(emptyList()) }
        assertFailsWith<ValidationException> { group.select(listOf(rare.id, medium.id)) }
    }

    @Test
    fun `selection rejects foreign and repeated options`() {
        val group = extras()
        val cheese = group.modifiers.first()

        assertFailsWith<ValidationException> { group.select(listOf(cheese.id, cheese.id)) }
        assertFailsWith<ValidationException> { group.select(listOf(doneness().modifiers.first().id)) }
        assertTrue(group.select(emptyList()).isEmpty())
    }

    @Test
    fun `failed limit change leaves the group unchanged`() {
        val group = doneness()

        assertFailsWith<ValidationException> { group.changeLimits(min = 5, max = 5) }

        assertEquals(1, group.minSelect)
        assertEquals(1, group.maxSelect)
    }
}
