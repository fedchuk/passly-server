package com.passly.application.kitchen

import com.passly.domain.order.OrderId
import com.passly.domain.order.OrderLineId
import com.passly.domain.order.PrepStatus
import com.passly.domain.venue.VenueId

data class MoveLinesCommand(
    val venueId: VenueId,
    val orderId: OrderId,
    val lineIds: List<OrderLineId>,
    val status: PrepStatus,
)
