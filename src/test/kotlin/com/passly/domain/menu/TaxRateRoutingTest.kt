package com.passly.domain.menu

import com.passly.domain.common.Money
import com.passly.domain.tax.VenueTaxRateId
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TaxRateRoutingTest {

    private val tenant = TenantId.new()
    private val venue = VenueId.new()
    private val food = VenueTaxRateId.new()
    private val alcohol = VenueTaxRateId.new()
    private val tree = MenuTree(venue, emptyList())
    private val drinks = tree.add(tenant, "Drinks", stationId = StationId.new(), taxRateId = food)
    private val wine = tree.add(tenant, "Wine", parentId = drinks.id, taxRateId = alcohol)
    private val reds = tree.add(tenant, "Reds", parentId = wine.id)
    private val juice = tree.add(tenant, "Juice", parentId = drinks.id)

    private fun item(category: Category, name: String, taxRateId: VenueTaxRateId? = null) =
        CategoryItems(venue, category.id, emptyList())
            .add(tenant, MenuItemDetails(name, null, Money.of("5.00"), taxRateId = taxRateId))

    @Test
    fun `item without its own rate uses the nearest category that has one`() {
        assertEquals(food, tree.taxRateFor(item(juice, "Orange juice")))
        assertEquals(alcohol, tree.taxRateFor(item(reds, "Rioja")))
    }

    @Test
    fun `own rate of the item wins over the category`() {
        val alcoholFree = item(reds, "Alcohol-free red", taxRateId = food)

        assertEquals(food, tree.taxRateFor(alcoholFree))
    }

    @Test
    fun `own rate is kept on update and dropped on archive`() {
        val rioja = item(reds, "Rioja", taxRateId = food)

        rioja.update(rioja.details.copy(price = Money.of("6.00")))
        assertEquals(food, rioja.taxRateId)

        val items = CategoryItems(venue, reds.id, listOf(rioja))
        items.archive(rioja.id)
        assertNull(rioja.taxRateId)
    }
}
