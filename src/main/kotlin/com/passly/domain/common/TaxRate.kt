package com.passly.domain.common

import java.math.BigDecimal
import java.math.RoundingMode

@JvmInline
value class TaxRate private constructor(val percent: BigDecimal) {

    override fun toString(): String = "${percent.toPlainString()}%"

    companion object {
        private val MAX = BigDecimal(100)

        fun of(percent: BigDecimal): TaxRate {
            if (percent.signum() < 0 || percent > MAX) {
                throw ValidationException("Tax rate $percent must be between 0 and 100")
            }
            if (percent.stripTrailingZeros().scale() > 2) {
                throw ValidationException("Tax rate $percent must have at most 2 decimal places")
            }
            return TaxRate(percent.setScale(2, RoundingMode.UNNECESSARY))
        }

        fun of(percent: String): TaxRate = of(BigDecimal(percent))
    }
}
