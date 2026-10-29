package com.passly.domain.discount

import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId
import com.passly.domain.venue.validName

class VenueDiscount private constructor(
    val id: VenueDiscountId,
    val tenantId: TenantId,
    val venueId: VenueId,
    name: String,
    percent: DiscountPercent,
    sortOrder: Int,
    val version: Long?,
) {
    var name: String = name
        internal set

    var percent: DiscountPercent = percent
        internal set

    var sortOrder: Int = sortOrder
        internal set

    val isNew: Boolean get() = version == null

    val label: String get() = "$name · ${percent.label}"

    override fun equals(other: Any?) = other is VenueDiscount && other.id == id

    override fun hashCode() = id.hashCode()

    override fun toString() = "VenueDiscount($id, '$name', $percent)"

    companion object {
        const val MAX_NAME_LENGTH = 80

        internal fun new(tenantId: TenantId, venueId: VenueId, name: String, percent: DiscountPercent, sortOrder: Int) =
            VenueDiscount(VenueDiscountId.new(), tenantId, venueId, validDiscountName(name), percent, sortOrder, null)

        fun fromStorage(
            id: VenueDiscountId,
            tenantId: TenantId,
            venueId: VenueId,
            name: String,
            percent: DiscountPercent,
            sortOrder: Int,
            version: Long,
        ) = VenueDiscount(id, tenantId, venueId, name, percent, sortOrder, version)

        internal fun validDiscountName(name: String): String = validName(name, "Discount", MAX_NAME_LENGTH)
    }
}
