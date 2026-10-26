package com.passly.domain.venue

import java.time.Instant

data class AcceptedCurrency(
    val currency: CurrencyCode,
    val rate: ExchangeRate,
    val rateSetAt: Instant,
)
