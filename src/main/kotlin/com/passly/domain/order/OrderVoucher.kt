package com.passly.domain.order

import com.passly.domain.user.UserId
import com.passly.domain.voucher.VoucherCode
import com.passly.domain.voucher.VoucherId
import com.passly.domain.voucher.VoucherKind
import com.passly.domain.voucher.VoucherValue
import java.time.Instant

data class OrderVoucher(
    val voucherId: VoucherId,
    val code: VoucherCode,
    val name: String,
    val kind: VoucherKind,
    val value: VoucherValue,
    val appliedBy: UserId,
    val appliedAt: Instant,
)
