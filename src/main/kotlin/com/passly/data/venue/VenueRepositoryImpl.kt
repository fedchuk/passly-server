package com.passly.data.venue

import com.passly.domain.venue.TenantId
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

    override fun findAllByTenant(tenantId: TenantId): List<Venue> =
        jpa.findAllByTenantId(tenantId.value).map { it.toDomain() }

    override fun findAllByIds(ids: Collection<VenueId>): List<Venue> =
        if (ids.isEmpty()) emptyList() else jpa.findAllById(ids.map { it.value }).map { it.toDomain() }

    override fun save(venue: Venue) {
        jpa.save(VenueEntity(venue.id.value, venue.tenantId.value, venue.name))
    }
}
