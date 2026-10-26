package com.passly.web.service

import com.passly.domain.order.OrderLineRequest
import java.util.*

class RoundForm {
    var orderId: UUID? = null
    var guests: Int? = null
    var lines: MutableList<RoundLineForm> = mutableListOf()

    fun requests(): List<OrderLineRequest> = lines.mapNotNull { it.toRequest() }
}
