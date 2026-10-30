package com.passly.data.voucher

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.*

interface VoucherJpaRepository : JpaRepository<VoucherEntity, UUID> {

    fun findByIdAndVenueId(id: UUID, venueId: UUID): VoucherEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from VoucherEntity v where v.id = :id and v.venueId = :venueId")
    fun findByIdAndVenueIdForUpdate(@Param("id") id: UUID, @Param("venueId") venueId: UUID): VoucherEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from VoucherEntity v where v.venueId = :venueId and v.code = :code")
    fun findByVenueIdAndCodeForUpdate(@Param("venueId") venueId: UUID, @Param("code") code: String): VoucherEntity?

    fun existsByVenueIdAndCode(venueId: UUID, code: String): Boolean

    fun findByVenueIdOrderByIssuedAtDescCodeAsc(venueId: UUID, limit: Limit): List<VoucherEntity>
}
