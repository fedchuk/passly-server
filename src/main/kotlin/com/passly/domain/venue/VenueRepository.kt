package com.passly.domain.venue

interface VenueRepository {
    fun findById(id: VenueId): Venue?
    fun findByIdAndLock(id: VenueId): Venue?
    fun findAllByTenant(tenantId: TenantId): List<Venue>
    fun findAllByIds(ids: Collection<VenueId>): List<Venue>
}
