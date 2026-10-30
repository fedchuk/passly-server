package com.passly.application.voucher

import com.passly.domain.venue.VenueId
import com.passly.domain.voucher.VoucherKind
import com.passly.domain.voucher.VoucherValue
import java.time.LocalDate

data class CreateVouchersCommand(
    val venueId: VenueId,
    val name: String,
    val kind: VoucherKind,
    val value: VoucherValue,
    val validUntil: LocalDate,
    val quantity: Int = 1,
)
