package com.passly.application.voucher

import com.passly.domain.venue.VenueId
import com.passly.domain.voucher.*

class FakeVoucherRepository : VoucherRepository {
    val stored = linkedMapOf<VoucherId, Voucher>()
    var locks = 0

    override fun find(venueId: VenueId, id: VoucherId) =
        stored[id]?.takeIf { it.venueId == venueId }?.let(::copyOf)

    override fun findForUpdate(venueId: VenueId, id: VoucherId) = find(venueId, id)?.also { locks++ }

    override fun findByCodeForUpdate(venueId: VenueId, code: VoucherCode) =
        stored.values.firstOrNull { it.venueId == venueId && it.code == code }?.let(::copyOf)?.also { locks++ }

    override fun existsByCode(venueId: VenueId, code: VoucherCode) =
        stored.values.any { it.venueId == venueId && it.code == code }

    override fun findLatest(venueId: VenueId, limit: Int) =
        stored.values.filter { it.venueId == venueId }.sortedByDescending { it.issuedAt }.take(limit).map(::copyOf)

    override fun save(voucher: Voucher) {
        stored[voucher.id] = copyOf(voucher, (voucher.version ?: -1) + 1)
    }

    override fun delete(voucher: Voucher) {
        stored.remove(voucher.id)
    }

    private fun copyOf(v: Voucher, version: Long = v.version ?: 0) = Voucher.fromStorage(
        v.id, v.tenantId, v.venueId, v.code, v.name, v.kind, v.value, v.validUntil, v.issuedAt,
        v.redeemedOrderId, v.redeemedAt, version,
    )
}
