package com.passly.application.tax

import com.passly.domain.tax.TaxRateList
import com.passly.domain.tax.TaxRateRepository
import com.passly.domain.tax.VenueTaxRate
import com.passly.domain.tax.VenueTaxRateId
import com.passly.domain.venue.VenueId

class FakeTaxRateRepository : TaxRateRepository {
    val stored = linkedMapOf<VenueTaxRateId, VenueTaxRate>()

    override fun load(venueId: VenueId) =
        TaxRateList(venueId, stored.values.filter { it.venueId == venueId }.map(::copyOf))

    override fun saveAll(rates: Collection<VenueTaxRate>) {
        rates.forEach { stored[it.id] = copyOf(it, (it.version ?: -1) + 1) }
    }

    override fun delete(rate: VenueTaxRate) {
        stored.remove(rate.id)
    }

    private fun copyOf(r: VenueTaxRate, version: Long = r.version ?: 0) =
        VenueTaxRate.fromStorage(r.id, r.tenantId, r.venueId, r.name, r.rate, r.sortOrder, version)
}
