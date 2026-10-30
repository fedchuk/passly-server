package com.passly.application.kitchen

import com.passly.domain.order.OrderLineId
import com.passly.domain.order.PrepStatus
import java.time.Instant

data class TicketLine(
    val lineId: OrderLineId,
    val name: String,
    val quantity: Int,
    val modifiers: List<String>,
    val comment: String?,
    val status: PrepStatus,
    val statusChangedAt: Instant,
)
