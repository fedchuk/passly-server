package com.passly.data.venue

import com.passly.domain.venue.Venue
import com.passly.domain.venue.VenueId
import com.passly.domain.venue.VenueRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class VenueRepositoryImpl(
    private val jpa: VenueJpaRepository,
) : VenueRepository {

    override fun findById(id: VenueId): Venue? =
        jpa.findByIdOrNull(id.value)?.toDomain()

    override fun findByIdAndLock(id: VenueId): Venue? =
        jpa.findByIdForUpdate(id.value)?.toDomain()
}
