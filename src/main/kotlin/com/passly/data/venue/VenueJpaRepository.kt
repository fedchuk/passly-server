package com.passly.data.venue

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface VenueJpaRepository : JpaRepository<VenueEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from VenueEntity v where v.id = :id")
    fun findByIdForUpdate(@Param("id") id: UUID): VenueEntity?

    fun findAllByTenantId(tenantId: UUID): List<VenueEntity>
}
