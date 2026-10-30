package com.passly.data.voucher

import com.passly.data.common.upsert
import com.passly.domain.venue.VenueId
import com.passly.domain.voucher.Voucher
import com.passly.domain.voucher.VoucherCode
import com.passly.domain.voucher.VoucherId
import com.passly.domain.voucher.VoucherRepository
import org.springframework.data.domain.Limit
import org.springframework.stereotype.Repository

@Repository
class VoucherRepositoryImpl(
    private val jpa: VoucherJpaRepository,
) : VoucherRepository {

    override fun find(venueId: VenueId, id: VoucherId): Voucher? =
        jpa.findByIdAndVenueId(id.value, venueId.value)?.toDomain()

    override fun findForUpdate(venueId: VenueId, id: VoucherId): Voucher? =
        jpa.findByIdAndVenueIdForUpdate(id.value, venueId.value)?.toDomain()

    override fun findByCodeForUpdate(venueId: VenueId, code: VoucherCode): Voucher? =
        jpa.findByVenueIdAndCodeForUpdate(venueId.value, code.value)?.toDomain()

    override fun existsByCode(venueId: VenueId, code: VoucherCode): Boolean =
        jpa.existsByVenueIdAndCode(venueId.value, code.value)

    override fun findLatest(venueId: VenueId, limit: Int): List<Voucher> =
        jpa.findByVenueIdOrderByIssuedAtDescCodeAsc(venueId.value, Limit.of(limit)).map { it.toDomain() }

    override fun save(voucher: Voucher) {
        jpa.upsert(voucher.id.value, voucher.version, create = { voucher.toNewEntity() }, update = { updateFrom(voucher) })
    }

    override fun delete(voucher: Voucher) {
        jpa.deleteById(voucher.id.value)
    }
}
