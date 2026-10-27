package com.passly.domain.order

import com.passly.domain.common.Money
import com.passly.domain.common.TaxRate
import com.passly.domain.floor.DiningTable
import com.passly.domain.floor.FloorPlan
import com.passly.domain.menu.*
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId

class OrderFixtures {
    val tenantId = TenantId.new()
    val venueId = VenueId.new()
    private val floor = FloorPlan(venueId, emptyList(), emptyList())
    private val hall = floor.addArea(tenantId, "Hall")
    private val items = CategoryItems(venueId, CategoryId.new(), emptyList())
    private val taxRates = mutableMapOf<MenuItemId, TaxRate>()

    fun table(label: String = "1"): DiningTable = floor.addTable(tenantId, hall.id, label, 4)

    fun item(name: String, price: String, taxRate: String = "10"): MenuItem =
        items.add(tenantId, MenuItemDetails(name, null, Money.of(price))).also { taxRates[it.id] = TaxRate.of(taxRate) }

    fun taxRateOf(item: MenuItem): TaxRate = taxRates.getValue(item.id)

    fun group(name: String, min: Int, max: Int, vararg options: Pair<String, String>): ModifierGroup =
        ModifierGroup.create(tenantId, venueId, name, min, max, options.map {
            ModifierDraft(
                it.first,
                Money.of(it.second)
            )
        })
}
