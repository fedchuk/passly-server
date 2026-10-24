package com.passly.domain.menu

import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId
import com.passly.domain.venue.validName

class Station private constructor(
    val id: StationId,
    val tenantId: TenantId,
    val venueId: VenueId,
    name: String,
    sortOrder: Int,
    val version: Long?,
) {
    var name: String = name
        internal set

    var sortOrder: Int = sortOrder
        internal set

    val isNew: Boolean get() = version == null

    override fun equals(other: Any?) = other is Station && other.id == id

    override fun hashCode() = id.hashCode()

    override fun toString() = "Station($id, '$name', order=$sortOrder)"

    companion object {
        const val MAX_NAME_LENGTH = 80

        internal fun new(tenantId: TenantId, venueId: VenueId, name: String, sortOrder: Int) =
            Station(StationId.new(), tenantId, venueId, validStationName(name), sortOrder, null)

        fun fromStorage(
            id: StationId,
            tenantId: TenantId,
            venueId: VenueId,
            name: String,
            sortOrder: Int,
            version: Long,
        ) = Station(id, tenantId, venueId, name, sortOrder, version)

        internal fun validStationName(name: String): String = validName(name, "Station", MAX_NAME_LENGTH)
    }
}
