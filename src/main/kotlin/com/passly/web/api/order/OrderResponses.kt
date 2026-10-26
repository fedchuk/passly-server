package com.passly.web.api.order

import com.passly.domain.order.Order
import com.passly.domain.order.OrderLine
import com.passly.domain.order.OrderStatus
import com.passly.domain.order.PaymentMethod
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.*

data class OrderResponse(
    val id: UUID,
    val tableId: UUID,
    val status: OrderStatus,
    val guests: Int?,
    val openedBy: UUID,
    val openedAt: Instant,
    @Schema(description = "Number of rounds sent so far")
    val rounds: Int,
    @Schema(description = "Sum of all lines before discounts", example = "31.90")
    val total: String,
    @Schema(description = "Sum of all payments", example = "20.00")
    val paid: String,
    @Schema(description = "What is still to pay", example = "11.90")
    val due: String,
    @Schema(description = "VAT included in the total, one entry per rate")
    val taxes: List<TaxShareResponse>,
    @Schema(description = "Lines in the order they were sent")
    val lines: List<OrderLineResponse>,
    val payments: List<PaymentResponse>,
    val closedBy: UUID?,
    val closedAt: Instant?,
)

data class TaxShareResponse(
    @Schema(example = "10.00")
    val rate: String,
    @Schema(description = "Amount with VAT", example = "29.80")
    val gross: String,
    @Schema(example = "2.71")
    val tax: String,
    @Schema(description = "Amount without VAT", example = "27.09")
    val net: String,
)

data class PaymentResponse(
    val id: UUID,
    val method: PaymentMethod,
    @Schema(example = "20.00")
    val amount: String,
    val takenBy: UUID,
    val takenAt: Instant,
)

data class OrderLineResponse(
    val id: UUID,
    @Schema(description = "Lines sent together share the round", example = "1")
    val round: Int,
    val menuItemId: UUID,
    @Schema(description = "Name at the time of the order")
    val name: String,
    @Schema(description = "Price of one portion with the chosen options, at the time of the order", example = "26.50")
    val unitPrice: String,
    @Schema(example = "10.00")
    val taxRate: String,
    @Schema(description = "Where the line is prepared")
    val stationId: UUID?,
    val quantity: Int,
    val comment: String?,
    @Schema(example = "26.50")
    val total: String,
    val modifiers: List<OrderLineModifierResponse>,
    val addedBy: UUID,
    val addedAt: Instant,
)

data class OrderLineModifierResponse(
    val modifierId: UUID,
    @Schema(example = "Doneness")
    val group: String,
    @Schema(example = "Well done")
    val name: String,
    @Schema(example = "0.50")
    val priceDelta: String,
)

fun Order.toResponse() = OrderResponse(
    id = id.value,
    tableId = tableId.value,
    status = status,
    guests = guests,
    openedBy = openedBy.value,
    openedAt = openedAt,
    rounds = rounds,
    total = total.toString(),
    paid = paid.toString(),
    due = due.toString(),
    taxes = taxShares.map { TaxShareResponse(it.rate.percent.toPlainString(), it.gross.toString(), it.tax.toString(), it.net.toString()) },
    lines = lines.map { it.toResponse() },
    payments = payments.map { PaymentResponse(it.id.value, it.method, it.amount.toString(), it.takenBy.value, it.takenAt) },
    closedBy = closedBy?.value,
    closedAt = closedAt,
)

private fun OrderLine.toResponse() = OrderLineResponse(
    id = id.value,
    round = round,
    menuItemId = menuItemId.value,
    name = name,
    unitPrice = unitPrice.toString(),
    taxRate = taxRate.percent.toPlainString(),
    stationId = stationId?.value,
    quantity = quantity,
    comment = comment,
    total = total.toString(),
    modifiers = modifiers.map { OrderLineModifierResponse(it.modifierId.value, it.groupName, it.name, it.priceDelta.toString()) },
    addedBy = addedBy.value,
    addedAt = addedAt,
)
