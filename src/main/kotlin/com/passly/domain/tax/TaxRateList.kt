package com.passly.domain.tax

import com.passly.domain.common.ConflictException
import com.passly.domain.common.NotFoundException
import com.passly.domain.common.TaxRate
import com.passly.domain.common.ValidationException
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId

class TaxRateList(
    val venueId: VenueId,
    rates: Collection<VenueTaxRate>,
) {
    private val byId: MutableMap<VenueTaxRateId, VenueTaxRate> = rates.associateByTo(LinkedHashMap()) { it.id }

    init {
        require(rates.all { it.venueId == venueId }) { "All tax rates must belong to venue $venueId" }
    }

    val rates: List<VenueTaxRate> get() = byId.values.sortedBy { it.sortOrder }

    operator fun get(id: VenueTaxRateId): VenueTaxRate = byId[id] ?: throw NotFoundException("Tax rate", id)

    fun add(tenantId: TenantId, name: String, rate: TaxRate): VenueTaxRate {
        if (byId.size >= MAX_RATES) throw ValidationException("A venue can have at most $MAX_RATES tax rates")
        val validName = VenueTaxRate.validTaxRateName(name)
        requireFreeName(validName, except = null)
        val taxRate = VenueTaxRate.new(tenantId, venueId, validName, rate, (byId.values.maxOfOrNull { it.sortOrder } ?: -1) + 1)
        byId[taxRate.id] = taxRate
        return taxRate
    }

    fun addMissing(tenantId: TenantId, presets: List<TaxRatePreset>): List<VenueTaxRate> =
        presets
            .filter { preset -> byId.values.none { it.name.equals(preset.name, ignoreCase = true) } }
            .take(MAX_RATES - byId.size)
            .map { add(tenantId, it.name, it.rate) }

    fun change(id: VenueTaxRateId, name: String, rate: TaxRate): VenueTaxRate {
        val taxRate = get(id)
        val validName = VenueTaxRate.validTaxRateName(name)
        requireFreeName(validName, except = id)
        taxRate.name = validName
        taxRate.rate = rate
        return taxRate
    }

    fun remove(id: VenueTaxRateId): Set<VenueTaxRate> {
        get(id)
        byId.remove(id)
        val changed = linkedSetOf<VenueTaxRate>()
        rates.forEachIndexed { index, taxRate ->
            if (taxRate.sortOrder != index) {
                taxRate.sortOrder = index
                changed += taxRate
            }
        }
        return changed
    }

    private fun requireFreeName(name: String, except: VenueTaxRateId?) {
        if (byId.values.any { it.id != except && it.name.equals(name, ignoreCase = true) }) {
            throw ConflictException("Tax rate '$name' already exists")
        }
    }

    companion object {
        const val MAX_RATES = 10
    }
}
