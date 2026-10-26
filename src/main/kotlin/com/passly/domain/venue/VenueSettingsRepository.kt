package com.passly.domain.venue

interface VenueSettingsRepository {
    fun find(venueId: VenueId): VenueSettings?
    fun save(settings: VenueSettings)
}
