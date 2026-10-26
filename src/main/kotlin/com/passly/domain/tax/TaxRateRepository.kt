package com.passly.domain.tax

import com.passly.domain.venue.VenueId

interface TaxRateRepository {
    fun load(venueId: VenueId): TaxRateList
    fun saveAll(rates: Collection<VenueTaxRate>)
    fun delete(rate: VenueTaxRate)
}
