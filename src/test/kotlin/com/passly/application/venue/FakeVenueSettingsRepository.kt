package com.passly.application.venue

import com.passly.domain.venue.VenueId
import com.passly.domain.venue.VenueSettings
import com.passly.domain.venue.VenueSettingsRepository

class FakeVenueSettingsRepository(vararg settings: VenueSettings) : VenueSettingsRepository {
    private val byVenue = settings.associateByTo(linkedMapOf()) { it.venueId }

    override fun find(venueId: VenueId) = byVenue[venueId]?.let { copyOf(it, it.version) }

    override fun save(settings: VenueSettings) {
        byVenue[settings.venueId] = copyOf(settings, settings.version + 1)
    }

    private fun copyOf(s: VenueSettings, version: Long) =
        VenueSettings.fromStorage(s.venueId, s.country, s.currency, version, s.acceptedCurrencies)
}
