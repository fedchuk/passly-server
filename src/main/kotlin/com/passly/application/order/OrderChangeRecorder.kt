package com.passly.application.order

import com.passly.domain.menu.StationId
import com.passly.domain.order.Order

interface OrderChangeRecorder {
    fun recordChange(order: Order, kind: OrderChangeKind, stationIds: Collection<StationId> = emptySet())
}
