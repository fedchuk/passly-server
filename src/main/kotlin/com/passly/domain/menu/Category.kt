package com.passly.domain.menu

import com.passly.domain.common.ValidationException
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId

class Category private constructor(
    val id: CategoryId,
    val tenantId: TenantId,
    val venueId: VenueId,
    parentId: CategoryId?,
    name: String,
    sortOrder: Int,
    stationId: StationId?,
    active: Boolean,
    val version: Long?,
) {
    var parentId: CategoryId? = parentId
        internal set

    var name: String = name
        private set

    var sortOrder: Int = sortOrder
        internal set

    var stationId: StationId? = stationId
        private set

    var active: Boolean = active
        private set

    val isNew: Boolean get() = version == null

    fun rename(newName: String) {
        name = validName(newName)
    }

    fun assignStation(station: StationId?) {
        stationId = station
    }

    fun activate() {
        active = true
    }

    fun deactivate() {
        active = false
    }

    override fun equals(other: Any?) = other is Category && other.id == id

    override fun hashCode() = id.hashCode()

    override fun toString() = "Category($id, '$name', parent=$parentId, order=$sortOrder)"

    companion object {
        const val MAX_NAME_LENGTH = 120

        internal fun new(
            tenantId: TenantId,
            venueId: VenueId,
            parentId: CategoryId?,
            name: String,
            sortOrder: Int,
            stationId: StationId?,
        ) = Category(
            id = CategoryId.new(),
            tenantId = tenantId,
            venueId = venueId,
            parentId = parentId,
            name = validName(name),
            sortOrder = sortOrder,
            stationId = stationId,
            active = true,
            version = null,
        )

        fun fromStorage(
            id: CategoryId,
            tenantId: TenantId,
            venueId: VenueId,
            parentId: CategoryId?,
            name: String,
            sortOrder: Int,
            stationId: StationId?,
            active: Boolean,
            version: Long,
        ) = Category(id, tenantId, venueId, parentId, name, sortOrder, stationId, active, version)

        private fun validName(name: String): String {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) throw ValidationException("Category name must not be blank")
            if (trimmed.length > MAX_NAME_LENGTH) {
                throw ValidationException("Category name is too long (max $MAX_NAME_LENGTH)")
            }
            return trimmed
        }
    }
}
