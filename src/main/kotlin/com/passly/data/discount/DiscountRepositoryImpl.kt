package com.passly.data.discount

import com.passly.data.common.upsert
import com.passly.domain.discount.DiscountList
import com.passly.domain.discount.DiscountRepository
import com.passly.domain.discount.VenueDiscount
import com.passly.domain.venue.VenueId
import org.springframework.stereotype.Repository

@Repository
class DiscountRepositoryImpl(
    private val jpa: VenueDiscountJpaRepository,
) : DiscountRepository {

    override fun load(venueId: VenueId): DiscountList =
        DiscountList(venueId, jpa.findAllByVenueIdOrderBySortOrderAscNameAsc(venueId.value).map { it.toDomain() })

    override fun saveAll(discounts: Collection<VenueDiscount>) {
        discounts.forEach { discount ->
            jpa.upsert(discount.id.value, discount.version, create = { discount.toNewEntity() }, update = { updateFrom(discount) })
        }
    }

    override fun delete(discount: VenueDiscount) {
        jpa.deleteById(discount.id.value)
    }
}
