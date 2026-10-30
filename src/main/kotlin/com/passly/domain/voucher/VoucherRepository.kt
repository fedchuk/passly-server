package com.passly.domain.voucher

import com.passly.domain.venue.VenueId

interface VoucherRepository {
    fun find(venueId: VenueId, id: VoucherId): Voucher?
    fun findForUpdate(venueId: VenueId, id: VoucherId): Voucher?
    fun findByCodeForUpdate(venueId: VenueId, code: VoucherCode): Voucher?
    fun existsByCode(venueId: VenueId, code: VoucherCode): Boolean
    fun findLatest(venueId: VenueId, limit: Int): List<Voucher>
    fun save(voucher: Voucher)
    fun delete(voucher: Voucher)
}
