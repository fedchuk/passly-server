package com.passly.data.role

import com.passly.data.common.upsert
import com.passly.domain.role.RoleList
import com.passly.domain.role.RoleRepository
import com.passly.domain.role.VenueRole
import com.passly.domain.venue.VenueId
import org.springframework.stereotype.Repository

@Repository
class RoleRepositoryImpl(
    private val jpa: VenueRoleJpaRepository,
) : RoleRepository {

    override fun load(venueId: VenueId): RoleList =
        RoleList(venueId, jpa.findAllByVenueIdOrderBySortOrderAscNameAsc(venueId.value).map { it.toDomain() })

    override fun saveAll(roles: Collection<VenueRole>) {
        roles.forEach { role ->
            jpa.upsert(role.id.value, role.version, create = { role.toNewEntity() }, update = { updateFrom(role) })
        }
    }

    override fun delete(role: VenueRole) {
        jpa.deleteById(role.id.value)
    }
}
