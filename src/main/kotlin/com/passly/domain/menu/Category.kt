package com.passly.domain.menu

import com.passly.domain.common.ValidationException
import com.passly.domain.tax.VenueTaxRateId
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
    taxRateId: VenueTaxRateId?,
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

    var taxRateId: VenueTaxRateId? = taxRateId
        internal set

    var active: Boolean = active
        private set

    val isNew: Boolean get() = version == null

    fun rename(newName: String) {
        name = validName(newName)
    }

    fun assignStation(station: StationId?) {
        if (parentId == null && station == null) {
            throw ValidationException("A top-level category needs a station", "stationId")
        }
        stationId = station
    }

    fun assignTaxRate(taxRate: VenueTaxRateId?) {
        if (parentId == null && taxRate == null) {
            throw ValidationException("A top-level category needs a tax rate", "taxRateId")
        }
        taxRateId = taxRate
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
            taxRateId: VenueTaxRateId?,
        ) = Category(
            id = CategoryId.new(),
            tenantId = tenantId,
            venueId = venueId,
            parentId = parentId,
            name = validName(name),
            sortOrder = sortOrder,
            stationId = stationId,
            taxRateId = taxRateId,
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
            taxRateId: VenueTaxRateId? = null,
        ) = Category(id, tenantId, venueId, parentId, name, sortOrder, stationId, taxRateId, active, version)

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
