package com.passly.domain.venue

interface VenueRepository {
    fun findById(id: VenueId): Venue?
    fun findByIdAndLock(id: VenueId): Venue?
}
