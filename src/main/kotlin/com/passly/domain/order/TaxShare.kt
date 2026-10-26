package com.passly.domain.order

import com.passly.domain.common.Money
import com.passly.domain.common.TaxRate
import java.math.BigDecimal
import java.math.RoundingMode

data class TaxShare(
    val rate: TaxRate,
    val gross: Money,
    val tax: Money,
) {
    val net: Money get() = gross - tax

    companion object {
        private val HUNDRED = BigDecimal(100)

        fun of(rate: TaxRate, gross: Money): TaxShare {
            val tax = gross.amount.multiply(rate.percent)
                .divide(HUNDRED.add(rate.percent), 2, RoundingMode.HALF_UP)
            return TaxShare(rate, gross, Money.of(tax))
        }
    }
}
