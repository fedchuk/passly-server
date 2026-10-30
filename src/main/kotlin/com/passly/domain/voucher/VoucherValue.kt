package com.passly.domain.voucher

import com.passly.domain.common.Money
import com.passly.domain.common.ValidationException
import com.passly.domain.discount.DiscountPercent

sealed interface VoucherValue {
    val label: String

    fun of(base: Money): Money

    data class Percent(val percent: DiscountPercent) : VoucherValue {
        override val label: String get() = percent.label

        override fun of(base: Money): Money = percent.of(base)
    }

    data class Amount(val amount: Money) : VoucherValue {
        init {
            if (amount.isNegative || amount.isZero) throw ValidationException("Voucher amount must be more than zero", "value")
            if (amount > MAX_AMOUNT) throw ValidationException("Voucher amount must be at most $MAX_AMOUNT", "value")
        }

        override val label: String get() = amount.toString()

        override fun of(base: Money): Money = minOf(amount, base)

        companion object {
            val MAX_AMOUNT: Money = Money.of("100000.00")
        }
    }
}
