package com.passly.web.api.order

import com.passly.application.order.OrderChangeKind
import io.swagger.v3.oas.annotations.media.Schema
import java.util.*

data class OrderChangeNotice(
    val orderId: UUID,
    val tableId: UUID,
    val change: OrderChangeKind,
    @Schema(description = "Stations whose lines were sent or moved; empty for changes of the bill")
    val stationIds: List<UUID>,
)
