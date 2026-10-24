package com.passly.domain.order

import com.passly.domain.common.Money
import com.passly.domain.common.ValidationException
import com.passly.domain.menu.StationId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LinePricingTest {

    private val fixtures = OrderFixtures()
    private val doneness = fixtures.group("Doneness", 1, 1, "Rare" to "0", "Medium" to "0")
    private val side = fixtures.group("Side", 0, 2, "Fries" to "0", "Vegetables" to "1.50", "Salad" to "-0.50")
    private val groups = listOf(doneness, side).associateBy { it.id }
    private val steak = fixtures.item("Ribeye", "26.00").apply { attachModifierGroups(listOf(doneness.id, side.id)) }

    private fun option(group: com.passly.domain.menu.ModifierGroup, name: String) = group.modifiers.single { it.name == name }.id

    @Test
    fun `unit price includes the chosen options and keeps their groups`() {
        val station = StationId.new()
        val request = OrderLineRequest(
            steak.id,
            quantity = 2,
            modifierIds = listOf(option(side, "Vegetables"), option(doneness, "Rare")),
            comment = "  no salt ",
        )

        val line = steak.priceLine(request, groups, station)

        assertEquals(Money.of("27.50"), line.unitPrice)
        assertEquals(listOf("Doneness: Rare", "Side: Vegetables"), line.modifiers.map { "${it.groupName}: ${it.name}" })
        assertEquals("no salt", line.comment)
        assertEquals(station, line.stationId)
        assertEquals(2, line.quantity)
    }

    @Test
    fun `a required group must be chosen`() {
        assertFailsWith<ValidationException> {
            steak.priceLine(OrderLineRequest(steak.id, 1), groups, null)
        }
    }

    @Test
    fun `an option of a group that is not attached is rejected`() {
        val milk = fixtures.group("Milk", 0, 1, "Oat" to "0.40")
        val request = OrderLineRequest(steak.id, 1, listOf(option(doneness, "Rare"), option(milk, "Oat")))

        val error = assertFailsWith<ValidationException> { steak.priceLine(request, groups + (milk.id to milk), null) }
        assertEquals("modifierIds", error.field)
    }

    @Test
    fun `quantity and comment are validated`() {
        val rare = option(doneness, "Rare")

        assertFailsWith<ValidationException> { steak.priceLine(OrderLineRequest(steak.id, 0, listOf(rare)), groups, null) }
        assertFailsWith<ValidationException> {
            steak.priceLine(OrderLineRequest(steak.id, MAX_LINE_QUANTITY + 1, listOf(rare)), groups, null)
        }
        assertFailsWith<ValidationException> {
            steak.priceLine(OrderLineRequest(steak.id, 1, listOf(rare), "x".repeat(MAX_LINE_COMMENT_LENGTH + 1)), groups, null)
        }
        assertNull(steak.priceLine(OrderLineRequest(steak.id, 1, listOf(rare), "   "), groups, null).comment)
    }

    @Test
    fun `discounts cannot make the price negative`() {
        val water = fixtures.item("Water", "0.30")
        val promo = fixtures.group("Promo", 0, 1, "Free" to "-0.50")
        water.attachModifierGroups(listOf(promo.id))

        assertFailsWith<ValidationException> {
            water.priceLine(OrderLineRequest(water.id, 1, listOf(promo.modifiers.single().id)), mapOf(promo.id to promo), null)
        }
    }
}
