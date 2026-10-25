package com.passly.application.order

import com.passly.domain.floor.DiningTableId
import com.passly.domain.floor.FloorPlan
import com.passly.domain.order.Order

data class FloorStatus(
    val plan: FloorPlan,
    val openOrders: Map<DiningTableId, Order>,
)
