package com.passly.application.kitchen

import com.passly.domain.order.OrderId
import com.passly.domain.order.PrepStatus
import java.time.Instant

data class StationTicket(
    val orderId: OrderId,
    val tableLabel: String,
    val round: Int,
    val sentAt: Instant,
    val sentBy: String,
    val lines: List<TicketLine>,
) {
    val status: PrepStatus get() = lines.minOf { it.status }
}
