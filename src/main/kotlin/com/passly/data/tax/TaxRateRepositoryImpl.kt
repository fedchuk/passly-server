package com.passly.data.tax

import com.passly.data.common.upsert
import com.passly.domain.tax.TaxRateList
import com.passly.domain.tax.TaxRateRepository
import com.passly.domain.tax.VenueTaxRate
import com.passly.domain.venue.VenueId
import org.springframework.stereotype.Repository

@Repository
class TaxRateRepositoryImpl(
    private val jpa: VenueTaxRateJpaRepository,
) : TaxRateRepository {

    override fun load(venueId: VenueId): TaxRateList =
        TaxRateList(venueId, jpa.findAllByVenueIdOrderBySortOrderAscNameAsc(venueId.value).map { it.toDomain() })

    override fun saveAll(rates: Collection<VenueTaxRate>) {
        rates.forEach { rate ->
            jpa.upsert(rate.id.value, rate.version, create = { rate.toNewEntity() }, update = { updateFrom(rate) })
        }
    }

    override fun delete(rate: VenueTaxRate) {
        jpa.deleteById(rate.id.value)
    }
}
