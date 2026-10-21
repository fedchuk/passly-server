package com.passly.data.floor

import com.passly.domain.floor.DiningAreaId
import com.passly.domain.floor.DiningTable
import com.passly.domain.floor.DiningTableId
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId

fun DiningTableEntity.toDomain() = DiningTable.fromStorage(
    id = DiningTableId(id),
    tenantId = TenantId(tenantId),
    venueId = VenueId(venueId),
    areaId = DiningAreaId(areaId),
    label = label,
    seats = seats,
    sortOrder = sortOrder,
    active = active,
    version = checkNotNull(version) { "Stored dining table $id has no version" },
)

fun DiningTable.toNewEntity() = DiningTableEntity(
    id = id.value,
    tenantId = tenantId.value,
    venueId = venueId.value,
    areaId = areaId.value,
    label = label,
    seats = seats,
    sortOrder = sortOrder,
    active = active,
)

fun DiningTableEntity.updateFrom(table: DiningTable) {
    areaId = table.areaId.value
    label = table.label
    seats = table.seats
    sortOrder = table.sortOrder
    active = table.active
}
