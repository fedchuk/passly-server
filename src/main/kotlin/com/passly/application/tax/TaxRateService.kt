package com.passly.application.tax

import com.passly.domain.common.ConflictException
import com.passly.domain.common.NotFoundException
import com.passly.domain.tax.TaxRatePreset
import com.passly.domain.tax.TaxRateRepository
import com.passly.domain.tax.VenueTaxRate
import com.passly.domain.tax.VenueTaxRateId
import com.passly.domain.venue.Venue
import com.passly.domain.venue.VenueId
import com.passly.domain.venue.VenueRepository
import com.passly.domain.venue.VenueSettingsRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class TaxRateService(
    private val venues: VenueRepository,
    private val settings: VenueSettingsRepository,
    private val taxRates: TaxRateRepository,
) {

    @Transactional(readOnly = true)
    fun list(venueId: VenueId): List<VenueTaxRate> {
        venues.findById(venueId) ?: throw NotFoundException("Venue", venueId)
        return taxRates.load(venueId).rates
    }

    @Transactional(readOnly = true)
    fun presets(venueId: VenueId): List<TaxRatePreset> {
        val country = settings.find(venueId)?.country ?: throw NotFoundException("Venue", venueId)
        val taken = taxRates.load(venueId).rates.map { it.name.lowercase() }.toSet()
        return TaxRatePreset.forCountry(country).filterNot { it.name.lowercase() in taken }
    }

    fun create(command: CreateTaxRateCommand): VenueTaxRate {
        val venue = lockVenue(command.venueId)
        val taxRate = taxRates.load(venue.id).add(venue.tenantId, command.name, command.rate)
        taxRates.saveAll(listOf(taxRate))
        return taxRate
    }

    fun addPresets(venueId: VenueId): List<VenueTaxRate> {
        val venue = lockVenue(venueId)
        val country = settings.find(venueId)?.country ?: throw NotFoundException("Venue", venueId)
        val added = taxRates.load(venue.id).addMissing(venue.tenantId, TaxRatePreset.forCountry(country))
        taxRates.saveAll(added)
        return added
    }

    fun change(command: ChangeTaxRateCommand): VenueTaxRate {
        lockVenue(command.venueId)
        val list = taxRates.load(command.venueId)
        val current = list[command.taxRateId]
        if (command.expectedVersion != null && command.expectedVersion != current.version) {
            throw ConflictException("Tax rate '${current.name}' was changed by someone else. Reload and try again.")
        }
        val taxRate = list.change(command.taxRateId, command.name, command.rate)
        taxRates.saveAll(listOf(taxRate))
        return taxRate
    }

    fun delete(venueId: VenueId, taxRateId: VenueTaxRateId): VenueTaxRate {
        lockVenue(venueId)
        val list = taxRates.load(venueId)
        val taxRate = list[taxRateId]
        val renumbered = list.remove(taxRateId)
        taxRates.delete(taxRate)
        taxRates.saveAll(renumbered)
        return taxRate
    }

    private fun lockVenue(id: VenueId): Venue =
        venues.findByIdAndLock(id) ?: throw NotFoundException("Venue", id)
}
