package com.passly.domain.common

import java.math.BigDecimal
import java.math.RoundingMode

@JvmInline
value class Money private constructor(val amount: BigDecimal) : Comparable<Money> {

    val isNegative: Boolean get() = amount.signum() < 0

    override fun compareTo(other: Money) = amount.compareTo(other.amount)

    override fun toString(): String = amount.toPlainString()

    companion object {
        private const val SCALE = 2

        fun of(amount: BigDecimal): Money {
            if (amount.stripTrailingZeros().scale() > SCALE) {
                throw ValidationException("Amount $amount must have at most $SCALE decimal places")
            }
            return Money(amount.setScale(SCALE, RoundingMode.UNNECESSARY))
        }

        fun of(amount: String): Money = of(BigDecimal(amount))
    }
}
