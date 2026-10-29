package com.passly.domain.discount

import com.passly.domain.common.ConflictException
import com.passly.domain.common.NotFoundException
import com.passly.domain.common.ValidationException
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId

class DiscountList(
    val venueId: VenueId,
    discounts: Collection<VenueDiscount>,
) {
    private val byId: MutableMap<VenueDiscountId, VenueDiscount> = discounts.associateByTo(LinkedHashMap()) { it.id }

    init {
        require(discounts.all { it.venueId == venueId }) { "All discounts must belong to venue $venueId" }
    }

    val discounts: List<VenueDiscount> get() = byId.values.sortedBy { it.sortOrder }

    operator fun get(id: VenueDiscountId): VenueDiscount = byId[id] ?: throw NotFoundException("Discount", id)

    fun add(tenantId: TenantId, name: String, percent: DiscountPercent): VenueDiscount {
        if (byId.size >= MAX_DISCOUNTS) throw ValidationException("A venue can have at most $MAX_DISCOUNTS discounts")
        val validName = VenueDiscount.validDiscountName(name)
        requireFreeName(validName, except = null)
        val discount = VenueDiscount.new(tenantId, venueId, validName, percent, (byId.values.maxOfOrNull { it.sortOrder } ?: -1) + 1)
        byId[discount.id] = discount
        return discount
    }

    fun change(id: VenueDiscountId, name: String, percent: DiscountPercent): VenueDiscount {
        val discount = get(id)
        val validName = VenueDiscount.validDiscountName(name)
        requireFreeName(validName, except = id)
        discount.name = validName
        discount.percent = percent
        return discount
    }

    fun remove(id: VenueDiscountId): Set<VenueDiscount> {
        get(id)
        byId.remove(id)
        val changed = linkedSetOf<VenueDiscount>()
        discounts.forEachIndexed { index, discount ->
            if (discount.sortOrder != index) {
                discount.sortOrder = index
                changed += discount
            }
        }
        return changed
    }

    private fun requireFreeName(name: String, except: VenueDiscountId?) {
        if (byId.values.any { it.id != except && it.name.equals(name, ignoreCase = true) }) {
            throw ConflictException("Discount '$name' already exists")
        }
    }

    companion object {
        const val MAX_DISCOUNTS = 20
    }
}
