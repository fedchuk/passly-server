package com.passly.application.order

data class OrderChangedEvent(
    val eventId: String,
    val type: String,
    val venueId: String,
    val orderId: String,
    val tableId: String,
    val change: OrderChangeKind,
    val stationIds: List<String>,
    val occurredAt: String,
) {
    companion object {
        const val TYPE = "OrderChanged"
    }
}
