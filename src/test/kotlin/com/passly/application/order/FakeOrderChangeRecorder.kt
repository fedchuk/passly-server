package com.passly.application.order

import com.passly.domain.menu.StationId
import com.passly.domain.order.Order
import com.passly.domain.order.OrderId

class FakeOrderChangeRecorder : OrderChangeRecorder {
    val recorded = mutableListOf<Triple<OrderId, OrderChangeKind, Set<StationId>>>()

    override fun recordChange(order: Order, kind: OrderChangeKind, stationIds: Collection<StationId>) {
        recorded += Triple(order.id, kind, stationIds.toSet())
    }

    fun kinds(): List<OrderChangeKind> = recorded.map { it.second }
}
