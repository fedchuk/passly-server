package com.passly.domain.floor

import com.passly.domain.common.ValidationException
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId

class DiningTable private constructor(
    val id: DiningTableId,
    val tenantId: TenantId,
    val venueId: VenueId,
    areaId: DiningAreaId,
    label: String,
    seats: Int,
    sortOrder: Int,
    active: Boolean,
    val version: Long?,
) {
    var areaId: DiningAreaId = areaId
        internal set

    var label: String = label
        internal set

    var seats: Int = seats
        internal set

    var sortOrder: Int = sortOrder
        internal set

    var active: Boolean = active
        internal set

    val isNew: Boolean get() = version == null

    override fun equals(other: Any?) = other is DiningTable && other.id == id

    override fun hashCode() = id.hashCode()

    override fun toString() = "DiningTable($id, '$label', area=$areaId, seats=$seats, order=$sortOrder)"

    companion object {
        const val MAX_LABEL_LENGTH = 20
        const val MAX_SEATS = 50

        internal fun new(
            tenantId: TenantId,
            venueId: VenueId,
            areaId: DiningAreaId,
            label: String,
            seats: Int,
            sortOrder: Int,
        ) = DiningTable(
            id = DiningTableId.new(),
            tenantId = tenantId,
            venueId = venueId,
            areaId = areaId,
            label = validLabel(label),
            seats = validSeats(seats),
            sortOrder = sortOrder,
            active = true,
            version = null,
        )

        fun fromStorage(
            id: DiningTableId,
            tenantId: TenantId,
            venueId: VenueId,
            areaId: DiningAreaId,
            label: String,
            seats: Int,
            sortOrder: Int,
            active: Boolean,
            version: Long,
        ) = DiningTable(id, tenantId, venueId, areaId, label, seats, sortOrder, active, version)

        internal fun validLabel(label: String): String {
            val trimmed = label.trim()
            if (trimmed.isEmpty()) throw ValidationException("Table label must not be blank", "label")
            if (trimmed.length > MAX_LABEL_LENGTH) {
                throw ValidationException("Table label is too long (max $MAX_LABEL_LENGTH)", "label")
            }
            return trimmed
        }

        internal fun validSeats(seats: Int): Int {
            if (seats !in 1..MAX_SEATS) throw ValidationException("Seats must be from 1 to $MAX_SEATS", "seats")
            return seats
        }
    }
}
