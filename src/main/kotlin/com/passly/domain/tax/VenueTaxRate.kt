package com.passly.domain.tax

import com.passly.domain.common.TaxRate
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId
import com.passly.domain.venue.validName

class VenueTaxRate private constructor(
    val id: VenueTaxRateId,
    val tenantId: TenantId,
    val venueId: VenueId,
    name: String,
    rate: TaxRate,
    sortOrder: Int,
    val version: Long?,
) {
    var name: String = name
        internal set

    var rate: TaxRate = rate
        internal set

    var sortOrder: Int = sortOrder
        internal set

    val isNew: Boolean get() = version == null

    val percentLabel: String get() = "${rate.percent.stripTrailingZeros().toPlainString()}%"

    override fun equals(other: Any?) = other is VenueTaxRate && other.id == id

    override fun hashCode() = id.hashCode()

    override fun toString() = "VenueTaxRate($id, '$name', $rate)"

    companion object {
        const val MAX_NAME_LENGTH = 80

        internal fun new(tenantId: TenantId, venueId: VenueId, name: String, rate: TaxRate, sortOrder: Int) =
            VenueTaxRate(VenueTaxRateId.new(), tenantId, venueId, validTaxRateName(name), rate, sortOrder, null)

        fun fromStorage(
            id: VenueTaxRateId,
            tenantId: TenantId,
            venueId: VenueId,
            name: String,
            rate: TaxRate,
            sortOrder: Int,
            version: Long,
        ) = VenueTaxRate(id, tenantId, venueId, name, rate, sortOrder, version)

        internal fun validTaxRateName(name: String): String = validName(name, "Tax rate", MAX_NAME_LENGTH)
    }
}
