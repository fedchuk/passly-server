package com.passly.application.order

import com.passly.domain.common.Money
import com.passly.domain.floor.DiningTableId
import com.passly.domain.order.OrderId
import com.passly.domain.order.OrderLineRequest
import com.passly.domain.order.PaymentMethod
import com.passly.domain.user.UserId
import com.passly.domain.venue.VenueId

data class OpenOrderCommand(
    val venueId: VenueId,
    val tableId: DiningTableId,
    val waiterId: UserId,
    val guests: Int? = null,
    val lines: List<OrderLineRequest> = emptyList(),
)

data class AddRoundCommand(
    val venueId: VenueId,
    val orderId: OrderId,
    val waiterId: UserId,
    val lines: List<OrderLineRequest>,
)

data class PayOrderCommand(
    val venueId: VenueId,
    val orderId: OrderId,
    val takenBy: UserId,
    val method: PaymentMethod,
    val amount: Money,
)

data class CloseOrderCommand(
    val venueId: VenueId,
    val orderId: OrderId,
    val closedBy: UserId,
)
