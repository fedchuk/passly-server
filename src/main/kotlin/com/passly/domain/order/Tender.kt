package com.passly.domain.order

import com.passly.domain.common.Money
import com.passly.domain.venue.CurrencyCode
import com.passly.domain.venue.ExchangeRate
import java.math.BigDecimal

data class Tender(
    val amount: Money,
    val currency: CurrencyCode,
    val rate: ExchangeRate?,
) {
    val inMainCurrency: Money get() = rate?.toMain(amount.amount) ?: amount

    val smallestCoinInMainCurrency: Money get() = rate?.toMain(ONE_CENT) ?: Money.ZERO

    val foreign: Boolean get() = rate != null

    companion object {
        private val ONE_CENT = BigDecimal("0.01")

        fun main(amount: Money, currency: CurrencyCode) = Tender(amount, currency, null)
    }
}
