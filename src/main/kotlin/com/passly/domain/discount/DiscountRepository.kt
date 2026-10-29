package com.passly.domain.discount

import com.passly.domain.venue.VenueId

interface DiscountRepository {
    fun load(venueId: VenueId): DiscountList
    fun saveAll(discounts: Collection<VenueDiscount>)
    fun delete(discount: VenueDiscount)
}
