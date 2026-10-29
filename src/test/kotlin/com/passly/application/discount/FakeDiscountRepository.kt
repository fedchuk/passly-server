package com.passly.application.discount

import com.passly.domain.discount.DiscountList
import com.passly.domain.discount.DiscountRepository
import com.passly.domain.discount.VenueDiscount
import com.passly.domain.discount.VenueDiscountId
import com.passly.domain.venue.VenueId

class FakeDiscountRepository : DiscountRepository {
    val stored = linkedMapOf<VenueDiscountId, VenueDiscount>()

    override fun load(venueId: VenueId) =
        DiscountList(venueId, stored.values.filter { it.venueId == venueId }.map(::copyOf))

    override fun saveAll(discounts: Collection<VenueDiscount>) {
        discounts.forEach { stored[it.id] = copyOf(it, (it.version ?: -1) + 1) }
    }

    override fun delete(discount: VenueDiscount) {
        stored.remove(discount.id)
    }

    private fun copyOf(d: VenueDiscount, version: Long = d.version ?: 0) =
        VenueDiscount.fromStorage(d.id, d.tenantId, d.venueId, d.name, d.percent, d.sortOrder, version)
}
