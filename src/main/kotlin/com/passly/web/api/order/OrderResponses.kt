package com.passly.web.api.order

import com.passly.domain.order.*
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
    @Schema(description = "Sum of all lines before the discount", example = "31.90")
    val total: String,
    @Schema(description = "Discount on the whole bill, copied from the venue discount when applied")
    val discount: OrderDiscountResponse?,
    @Schema(description = "Total minus the discount", example = "28.71")
    val toPay: String,
    @Schema(description = "Sum of all payments", example = "20.00")
    val paid: String,
    @Schema(description = "What is still to pay: toPay minus paid", example = "8.71")
    val due: String,
    @Schema(description = "VAT included in toPay, one entry per rate. The discount lowers every rate in proportion.")
    val taxes: List<TaxShareResponse>,
    @Schema(description = "Lines in the order they were sent")
    val lines: List<OrderLineResponse>,
    val payments: List<PaymentResponse>,
    val closedBy: UUID?,
    val closedAt: Instant?,
)

data class OrderDiscountResponse(
    @Schema(description = "The venue discount it was taken from. It may be changed or deleted since.")
    val discountId: UUID,
    @Schema(example = "Regular guest")
    val name: String,
    @Schema(example = "10.00")
    val percent: String,
    @Schema(description = "Taken off the total", example = "3.19")
    val amount: String,
    val appliedBy: UUID,
    val appliedAt: Instant,
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
    @Schema(description = "Counted towards the bill, in the main currency", example = "20.00")
    val amount: String,
    @Schema(description = "What the guest handed over, in the payment currency", example = "25.00")
    val tendered: String,
    @Schema(example = "EUR")
    val currency: String,
    @Schema(description = "Main currency units for one unit of the payment currency. Empty for the main currency.", example = "0.95")
    val rate: String?,
    @Schema(description = "Change returned in the main currency", example = "3.75")
    val change: String,
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
    discount = discount?.let {
        OrderDiscountResponse(it.discountId.value, it.name, it.percent.value.toPlainString(), discountAmount.toString(), it.appliedBy.value, it.appliedAt)
    },
    toPay = toPay.toString(),
    paid = paid.toString(),
    due = due.toString(),
    taxes = taxShares.map { TaxShareResponse(it.rate.percent.toPlainString(), it.gross.toString(), it.tax.toString(), it.net.toString()) },
    lines = lines.map { it.toResponse() },
    payments = payments.map { it.toResponse() },
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

private fun Payment.toResponse() = PaymentResponse(
    id = id.value,
    method = method,
    amount = amount.toString(),
    tendered = tender.amount.toString(),
    currency = tender.currency.value,
    rate = tender.rate?.toString(),
    change = change.toString(),
    takenBy = takenBy.value,
    takenAt = takenAt,
)
