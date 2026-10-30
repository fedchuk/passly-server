package com.passly.web.api.kitchen

import com.passly.application.kitchen.StationTicket
import com.passly.domain.order.PrepStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.*

data class TicketResponse(
    val orderId: UUID,
    @Schema(example = "7")
    val table: String,
    @Schema(example = "2")
    val round: Int,
    val sentAt: Instant,
    @Schema(example = "Anna")
    val sentBy: String,
    @Schema(description = "The least advanced status of its lines")
    val status: PrepStatus,
    val lines: List<TicketLineResponse>,
)

data class TicketLineResponse(
    val lineId: UUID,
    @Schema(example = "Ribeye")
    val name: String,
    @Schema(example = "2")
    val quantity: Int,
    @Schema(example = "[\"Medium rare\", \"Fries\"]")
    val modifiers: List<String>,
    @Schema(example = "No salt")
    val comment: String?,
    val status: PrepStatus,
    val statusChangedAt: Instant,
)

data class MovedLineResponse(
    val lineId: UUID,
    val status: PrepStatus,
    val statusChangedAt: Instant,
)

fun StationTicket.toResponse() = TicketResponse(
    orderId = orderId.value,
    table = tableLabel,
    round = round,
    sentAt = sentAt,
    sentBy = sentBy,
    status = status,
    lines = lines.map {
        TicketLineResponse(it.lineId.value, it.name, it.quantity, it.modifiers, it.comment, it.status, it.statusChangedAt)
    },
)
