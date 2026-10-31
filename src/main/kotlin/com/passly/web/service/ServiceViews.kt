package com.passly.web.service

import com.passly.application.order.FloorStatus
import com.passly.application.order.TableStatus
import com.passly.domain.order.*
import com.passly.domain.venue.VenueSettings
import java.time.Duration
import java.time.Instant
import java.util.*

data class ServiceAreaView(
    val id: UUID,
    val name: String,
    val tables: List<ServiceTableView>,
)

data class ServiceTableView(
    val id: UUID,
    val label: String,
    val seats: Int,
    val busy: Boolean,
    val total: String?,
    val guests: Int?,
    val openFor: String?,
    val ready: Int,
)

data class FloorSummaryView(
    val busy: Int,
    val total: Int,
)

data class TablePageView(
    val id: UUID,
    val label: String,
    val area: String,
    val seats: Int,
    val order: OrderPageView?,
)

data class OrderPageView(
    val id: UUID,
    val guests: Int?,
    val openFor: String,
    val total: String,
    val discount: String?,
    val discountId: UUID?,
    val discountAmount: String,
    val discountable: Boolean,
    val voucher: String?,
    val voucherAmount: String,
    val toPay: String,
    val paid: String,
    val due: String,
    val settled: Boolean,
    val partlyPaid: Boolean,
    val taxes: List<TaxLineView>,
    val payments: List<PaymentView>,
    val rounds: List<RoundView>,
    val readyLineIds: List<UUID>,
)

data class TaxLineView(
    val rate: String,
    val tax: String,
)

data class PaymentView(
    val method: String,
    val amount: String,
    val foreign: String?,
    val change: String?,
)

data class PaymentCurrencyView(
    val code: String,
    val rate: String?,
)

data class RoundView(
    val number: Int,
    val lines: List<LineView>,
)

data class LineView(
    val lineId: UUID,
    val quantity: Int,
    val name: String,
    val modifiers: String?,
    val comment: String?,
    val total: String,
    val status: String,
    val statusKey: String,
    val ready: Boolean,
)

fun FloorStatus.toAreaViews(now: Instant): List<ServiceAreaView> =
    plan.areas.map { area ->
        ServiceAreaView(
            id = area.id.value,
            name = area.name,
            tables = plan.tablesIn(area.id)
                .filter { it.active || it.id in openOrders }
                .map { table ->
                    val order = openOrders[table.id]
                    ServiceTableView(
                        id = table.id.value,
                        label = table.label,
                        seats = table.seats,
                        busy = order != null,
                        total = order?.toPay?.toString(),
                        guests = order?.guests,
                        openFor = order?.let { Duration.between(it.openedAt, now).toElapsedLabel() },
                        ready = order?.lines?.count { it.status == PrepStatus.READY } ?: 0,
                    )
                },
        )
    }.filter { it.tables.isNotEmpty() }

fun List<ServiceAreaView>.summary() = FloorSummaryView(
    busy = sumOf { area -> area.tables.count { it.busy } },
    total = sumOf { it.tables.size },
)

fun TableStatus.toPageView(now: Instant) = TablePageView(
    id = table.id.value,
    label = table.label,
    area = area.name,
    seats = table.seats,
    order = order?.toPageView(now),
)

private fun Order.toPageView(now: Instant) = OrderPageView(
    id = id.value,
    guests = guests,
    openFor = Duration.between(openedAt, now).toElapsedLabel(),
    total = total.toString(),
    discount = discount?.let { "${it.name} · ${it.percent.label}" },
    discountId = discount?.discountId?.value,
    discountAmount = discountAmount.toString(),
    discountable = payments.isEmpty(),
    voucher = voucher?.let { "Voucher ${it.code.label} · ${it.name}" },
    voucherAmount = voucherAmount.toString(),
    toPay = toPay.toString(),
    paid = paid.toString(),
    due = due.toString(),
    settled = due.isZero,
    partlyPaid = payments.isNotEmpty(),
    taxes = taxShares.map { TaxLineView("${it.rate.percent.stripTrailingZeros().toPlainString()}%", it.tax.toString()) },
    payments = payments.map { it.toView() },
    rounds = lines.groupBy { it.round }.toSortedMap().map { (number, lines) -> RoundView(number, lines.map { it.toView() }) },
    readyLineIds = lines.filter { it.status == PrepStatus.READY }.map { it.id.value },
)

private fun Payment.toView() = PaymentView(
    method = method.label(),
    amount = amount.toString(),
    foreign = tender.rate?.let { rate -> "${tender.amount} ${tender.currency} at $rate" },
    change = change.takeUnless { it.isZero }?.toString(),
)

fun Payment.takenLabel(): String =
    if (tender.foreign) "${method.label()} ${tender.amount} ${tender.currency} (${tender.inMainCurrency})"
    else "${method.label()} ${tender.amount}"

fun VenueSettings.toPaymentCurrencies(): List<PaymentCurrencyView> =
    listOf(PaymentCurrencyView(currency.value, null)) +
            acceptedCurrencies.map { PaymentCurrencyView(it.currency.value, it.rate.toString()) }

private fun OrderLine.toView() = LineView(
    lineId = id.value,
    quantity = quantity,
    name = name,
    modifiers = modifiers.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.name },
    comment = comment,
    total = total.toString(),
    status = status.waiterLabel(),
    statusKey = status.name.lowercase().replace('_', '-'),
    ready = status == PrepStatus.READY,
)

fun PrepStatus.waiterLabel(): String = when (this) {
    PrepStatus.NEW -> "Sent"
    PrepStatus.IN_PROGRESS -> "Preparing"
    PrepStatus.READY -> "Ready"
    PrepStatus.SERVED -> "Served"
}

fun Duration.toElapsedLabel(): String {
    val minutes = toMinutes().coerceAtLeast(0)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min"
        else -> "${minutes / 60} h ${"%02d".format(minutes % 60)} min"
    }
}

fun PaymentMethod.label(): String = when (this) {
    PaymentMethod.CASH -> "Cash"
    PaymentMethod.CARD -> "Card"
}
