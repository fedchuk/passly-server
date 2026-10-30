package com.passly.application.voucher

import com.passly.domain.common.ConflictException
import com.passly.domain.common.NotFoundException
import com.passly.domain.common.ValidationException
import com.passly.domain.venue.VenueId
import com.passly.domain.venue.VenueRepository
import com.passly.domain.venue.VenueSettingsRepository
import com.passly.domain.voucher.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Clock
import java.time.LocalDate

@Service
@Transactional
class VoucherService(
    private val venues: VenueRepository,
    private val settings: VenueSettingsRepository,
    private val vouchers: VoucherRepository,
    private val clock: Clock,
) {
    private val random = SecureRandom()

    @Transactional(readOnly = true)
    fun list(venueId: VenueId): List<Voucher> {
        venues.findById(venueId) ?: throw NotFoundException("Venue", venueId)
        return vouchers.findLatest(venueId, LIST_LIMIT)
    }

    @Transactional(readOnly = true)
    fun today(venueId: VenueId): LocalDate =
        (settings.find(venueId) ?: throw NotFoundException("Venue", venueId)).today(clock.instant())

    fun create(command: CreateVouchersCommand): List<Voucher> {
        val venue = venues.findById(command.venueId) ?: throw NotFoundException("Venue", command.venueId)
        if (command.kind == VoucherKind.REUSABLE && command.quantity != 1) {
            throw ValidationException("A reusable voucher is created one at a time", "quantity")
        }
        if (command.quantity !in 1..MAX_BATCH) throw ValidationException("Create from 1 to $MAX_BATCH vouchers at once", "quantity")
        val now = clock.instant()
        val today = today(venue.id)
        val codes = mutableSetOf<VoucherCode>()
        return List(command.quantity) {
            val code = freeCode(venue.id, codes)
            codes += code
            Voucher.create(venue.tenantId, venue.id, code, command.name, command.kind, command.value, command.validUntil, today, now)
                .also(vouchers::save)
        }
    }

    fun delete(venueId: VenueId, id: VoucherId): Voucher {
        val voucher = vouchers.findForUpdate(venueId, id) ?: throw NotFoundException("Voucher", id)
        voucher.requireDeletable()
        vouchers.delete(voucher)
        return voucher
    }

    private fun freeCode(venueId: VenueId, taken: Set<VoucherCode>): VoucherCode {
        repeat(CODE_ATTEMPTS) {
            val code = VoucherCode.generate(random)
            if (code !in taken && !vouchers.existsByCode(venueId, code)) return code
        }
        throw ConflictException("Could not find a free voucher code. Try again.")
    }

    companion object {
        const val MAX_BATCH = 50
        const val LIST_LIMIT = 200
        private const val CODE_ATTEMPTS = 10
    }
}
