package com.passly.web.kitchen

import com.passly.application.kitchen.StationTicket
import com.passly.application.kitchen.TicketLine
import com.passly.domain.menu.Station
import com.passly.domain.order.PrepStatus
import com.passly.web.service.toElapsedLabel
import java.time.Duration
import java.time.Instant
import java.util.*

data class StationTabView(
    val id: UUID,
    val name: String,
    val current: Boolean,
)

data class PrepActionView(
    val status: String,
    val label: String,
)

data class TicketLineView(
    val lineId: UUID,
    val quantity: Int,
    val name: String,
    val modifiers: String?,
    val comment: String?,
    val status: String,
    val statusKey: String,
    val action: PrepActionView?,
)

data class TicketView(
    val orderId: UUID,
    val table: String,
    val round: Int,
    val waitingFor: String,
    val sentBy: String,
    val late: Boolean,
    val action: PrepActionView?,
    val lines: List<TicketLineView>,
) {
    val lineIds: List<UUID> get() = lines.map { it.lineId }
}

data class StationBoardView(
    val preparing: List<TicketView>,
    val ready: List<TicketView>,
)

private val LATE_AFTER: Duration = Duration.ofMinutes(15)

fun List<Station>.toTabs(currentId: UUID?) = map { StationTabView(it.id.value, it.name, it.id.value == currentId) }

fun List<StationTicket>.toBoard(now: Instant): StationBoardView {
    val views = map { it.toView(now) }
    val readyOrders = filter { it.status == PrepStatus.READY }.map { it.orderId.value to it.round }.toSet()
    val (ready, preparing) = views.partition { (it.orderId to it.round) in readyOrders }
    return StationBoardView(preparing, ready)
}

private fun StationTicket.toView(now: Instant): TicketView {
    val waiting = Duration.between(sentAt, now)
    return TicketView(
        orderId = orderId.value,
        table = "Table $tableLabel",
        round = round,
        waitingFor = waiting.toElapsedLabel(),
        sentBy = sentBy,
        late = status.isBefore(PrepStatus.READY) && waiting > LATE_AFTER,
        action = status.ticketAction(),
        lines = lines.map { it.toView() },
    )
}

private fun TicketLine.toView() = TicketLineView(
    lineId = lineId.value,
    quantity = quantity,
    name = name,
    modifiers = modifiers.takeIf { it.isNotEmpty() }?.joinToString(", "),
    comment = comment,
    status = status.label(),
    statusKey = status.name.lowercase().replace('_', '-'),
    action = status.lineAction(),
)

fun PrepStatus.label(): String = when (this) {
    PrepStatus.NEW -> "New"
    PrepStatus.IN_PROGRESS -> "In progress"
    PrepStatus.READY -> "Ready"
    PrepStatus.SERVED -> "Served"
}

private fun PrepStatus.lineAction(): PrepActionView? = when (this) {
    PrepStatus.NEW -> PrepActionView(PrepStatus.IN_PROGRESS.name, "Start")
    PrepStatus.IN_PROGRESS -> PrepActionView(PrepStatus.READY.name, "Ready")
    PrepStatus.READY -> PrepActionView(PrepStatus.SERVED.name, "Picked up")
    PrepStatus.SERVED -> null
}

private fun PrepStatus.ticketAction(): PrepActionView? = when (this) {
    PrepStatus.NEW -> PrepActionView(PrepStatus.IN_PROGRESS.name, "Start all")
    PrepStatus.IN_PROGRESS -> PrepActionView(PrepStatus.READY.name, "All ready")
    PrepStatus.READY -> PrepActionView(PrepStatus.SERVED.name, "All picked up")
    PrepStatus.SERVED -> null
}
