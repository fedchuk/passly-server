package com.passly.domain.floor

import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId
import com.passly.domain.venue.validName

class DiningArea private constructor(
    val id: DiningAreaId,
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

    override fun equals(other: Any?) = other is DiningArea && other.id == id

    override fun hashCode() = id.hashCode()

    override fun toString() = "DiningArea($id, '$name', order=$sortOrder)"

    companion object {
        const val MAX_NAME_LENGTH = 80

        internal fun new(tenantId: TenantId, venueId: VenueId, name: String, sortOrder: Int) = DiningArea(
            id = DiningAreaId.new(),
            tenantId = tenantId,
            venueId = venueId,
            name = validAreaName(name),
            sortOrder = sortOrder,
            version = null,
        )

        fun fromStorage(
            id: DiningAreaId,
            tenantId: TenantId,
            venueId: VenueId,
            name: String,
            sortOrder: Int,
            version: Long,
        ) = DiningArea(id, tenantId, venueId, name, sortOrder, version)

        internal fun validAreaName(name: String): String = validName(name, "Area", MAX_NAME_LENGTH)
    }
}
