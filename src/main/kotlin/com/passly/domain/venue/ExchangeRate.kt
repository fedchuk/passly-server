package com.passly.domain.venue

import com.passly.domain.common.Money
import com.passly.domain.common.ValidationException
import java.math.BigDecimal
import java.math.RoundingMode

@JvmInline
value class ExchangeRate private constructor(val value: BigDecimal) {

    fun toMain(amount: BigDecimal): Money = Money.of(amount.multiply(value).setScale(2, RoundingMode.HALF_UP))

    override fun toString(): String = value.stripTrailingZeros().toPlainString()

    companion object {
        private const val MAX_DECIMALS = 6
        private val MAX = BigDecimal(1_000_000)

        fun of(value: BigDecimal): ExchangeRate {
            if (value.signum() <= 0) throw ValidationException("Exchange rate must be greater than zero", "rate")
            if (value >= MAX) throw ValidationException("Exchange rate $value is too large", "rate")
            if (value.stripTrailingZeros().scale() > MAX_DECIMALS) {
                throw ValidationException("Exchange rate $value must have at most $MAX_DECIMALS decimal places", "rate")
            }
            return ExchangeRate(value.setScale(MAX_DECIMALS, RoundingMode.UNNECESSARY))
        }

        fun of(value: String): ExchangeRate =
            of(value.trim().toBigDecimalOrNull() ?: throw ValidationException("Exchange rate '$value' is not a number", "rate"))
    }
}
