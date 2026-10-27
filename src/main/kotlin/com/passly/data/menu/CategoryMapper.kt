package com.passly.data.menu

import com.passly.domain.menu.Category
import com.passly.domain.menu.CategoryId
import com.passly.domain.menu.StationId
import com.passly.domain.tax.VenueTaxRateId
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId

fun CategoryEntity.toDomain() = Category.fromStorage(
    id = CategoryId(id),
    tenantId = TenantId(tenantId),
    venueId = VenueId(venueId),
    parentId = parentId?.let(::CategoryId),
    name = name,
    sortOrder = sortOrder,
    stationId = stationId?.let(::StationId),
    active = active,
    version = checkNotNull(version) { "Stored category $id has no version" },
    taxRateId = taxRateId?.let(::VenueTaxRateId),
)

fun Category.toNewEntity() = CategoryEntity(
    id = id.value,
    tenantId = tenantId.value,
    venueId = venueId.value,
    parentId = parentId?.value,
    name = name,
    sortOrder = sortOrder,
    stationId = stationId?.value,
    taxRateId = taxRateId?.value,
    active = active,
)

fun CategoryEntity.updateFrom(category: Category) {
    parentId = category.parentId?.value
    name = category.name
    sortOrder = category.sortOrder
    stationId = category.stationId?.value
    taxRateId = category.taxRateId?.value
    active = category.active
}
