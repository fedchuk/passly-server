package com.passly.data.menu

import com.passly.domain.menu.Station
import com.passly.domain.menu.StationId
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId

fun StationEntity.toDomain() = Station.fromStorage(
    id = StationId(id),
    tenantId = TenantId(tenantId),
    venueId = VenueId(venueId),
    name = name,
    sortOrder = sortOrder,
    version = checkNotNull(version) { "Stored station $id has no version" },
)

fun Station.toNewEntity() = StationEntity(
    id = id.value,
    tenantId = tenantId.value,
    venueId = venueId.value,
    name = name,
    sortOrder = sortOrder,
)

fun StationEntity.updateFrom(station: Station) {
    name = station.name
    sortOrder = station.sortOrder
}
