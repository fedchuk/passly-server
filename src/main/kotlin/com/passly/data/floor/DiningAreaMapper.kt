package com.passly.data.floor

import com.passly.domain.floor.DiningArea
import com.passly.domain.floor.DiningAreaId
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId

fun DiningAreaEntity.toDomain() = DiningArea.fromStorage(
    id = DiningAreaId(id),
    tenantId = TenantId(tenantId),
    venueId = VenueId(venueId),
    name = name,
    sortOrder = sortOrder,
    version = checkNotNull(version) { "Stored dining area $id has no version" },
)

fun DiningArea.toNewEntity() = DiningAreaEntity(
    id = id.value,
    tenantId = tenantId.value,
    venueId = venueId.value,
    name = name,
    sortOrder = sortOrder,
)

fun DiningAreaEntity.updateFrom(area: DiningArea) {
    name = area.name
    sortOrder = area.sortOrder
}
