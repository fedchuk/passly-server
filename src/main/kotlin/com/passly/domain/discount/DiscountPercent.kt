package com.passly.domain.discount

import com.passly.domain.common.Money
import com.passly.domain.common.ValidationException
import java.math.BigDecimal
import java.math.RoundingMode

@JvmInline
value class DiscountPercent private constructor(val value: BigDecimal) {

    val label: String get() = "${value.stripTrailingZeros().toPlainString()}%"

    fun of(amount: Money): Money =
        Money.of(amount.amount.multiply(value).divide(HUNDRED, 2, RoundingMode.HALF_UP))

    override fun toString(): String = label

    companion object {
        private val HUNDRED = BigDecimal(100)

        fun of(percent: BigDecimal): DiscountPercent {
            if (percent.signum() <= 0 || percent > HUNDRED) {
                throw ValidationException("Discount must be more than 0% and at most 100%", "percent")
            }
            if (percent.stripTrailingZeros().scale() > 2) {
                throw ValidationException("Discount $percent must have at most 2 decimal places", "percent")
            }
            return DiscountPercent(percent.setScale(2, RoundingMode.UNNECESSARY))
        }

        fun of(percent: String): DiscountPercent = of(BigDecimal(percent))
    }
}
