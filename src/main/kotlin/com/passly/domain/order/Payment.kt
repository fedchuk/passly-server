package com.passly.domain.order

import com.passly.domain.common.Money
import com.passly.domain.user.UserId
import java.time.Instant

data class Payment(
    val id: PaymentId,
    val method: PaymentMethod,
    val amount: Money,
    val tender: Tender,
    val change: Money,
    val takenBy: UserId,
    val takenAt: Instant,
)
