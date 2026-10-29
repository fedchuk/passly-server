package com.passly.data.discount

import com.passly.domain.discount.DiscountPercent
import com.passly.domain.discount.VenueDiscount
import com.passly.domain.discount.VenueDiscountId
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId

fun VenueDiscountEntity.toDomain() = VenueDiscount.fromStorage(
    id = VenueDiscountId(id),
    tenantId = TenantId(tenantId),
    venueId = VenueId(venueId),
    name = name,
    percent = DiscountPercent.of(percent),
    sortOrder = sortOrder,
    version = checkNotNull(version) { "Stored discount $id has no version" },
)

fun VenueDiscount.toNewEntity() = VenueDiscountEntity(
    id = id.value,
    tenantId = tenantId.value,
    venueId = venueId.value,
    name = name,
    percent = percent.value,
    sortOrder = sortOrder,
)

fun VenueDiscountEntity.updateFrom(discount: VenueDiscount) {
    name = discount.name
    percent = discount.percent.value
    sortOrder = discount.sortOrder
}
