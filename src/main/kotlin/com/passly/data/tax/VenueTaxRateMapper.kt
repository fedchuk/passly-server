package com.passly.data.tax

import com.passly.domain.common.TaxRate
import com.passly.domain.tax.VenueTaxRate
import com.passly.domain.tax.VenueTaxRateId
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId

fun VenueTaxRateEntity.toDomain() = VenueTaxRate.fromStorage(
    id = VenueTaxRateId(id),
    tenantId = TenantId(tenantId),
    venueId = VenueId(venueId),
    name = name,
    rate = TaxRate.of(rate),
    sortOrder = sortOrder,
    version = checkNotNull(version) { "Stored tax rate $id has no version" },
)

fun VenueTaxRate.toNewEntity() = VenueTaxRateEntity(
    id = id.value,
    tenantId = tenantId.value,
    venueId = venueId.value,
    name = name,
    rate = rate.percent,
    sortOrder = sortOrder,
)

fun VenueTaxRateEntity.updateFrom(taxRate: VenueTaxRate) {
    name = taxRate.name
    rate = taxRate.rate.percent
    sortOrder = taxRate.sortOrder
}
