package com.passly.web.admin

import com.passly.domain.common.Money
import com.passly.domain.common.ValidationException
import com.passly.domain.discount.DiscountPercent
import com.passly.domain.voucher.Voucher
import com.passly.domain.voucher.VoucherKind
import com.passly.domain.voucher.VoucherValue
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.*

data class VoucherRowView(
    val id: UUID,
    val code: String,
    val name: String,
    val value: String,
    val kind: String,
    val validUntil: String,
    val status: String,
    val active: Boolean,
    val deletable: Boolean,
)

private val VOUCHER_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

fun Voucher.toRowView(today: LocalDate, currency: String) = VoucherRowView(
    id = id.value,
    code = code.label,
    name = name,
    value = value.offLabel(currency),
    kind = kind.label(),
    validUntil = VOUCHER_DATE.format(validUntil),
    status = when {
        isRedeemed -> "Used"
        isExpiredOn(today) -> "Expired"
        else -> "Active"
    },
    active = !isRedeemed && !isExpiredOn(today),
    deletable = !isRedeemed,
)

fun VoucherValue.offLabel(currency: String): String = when (this) {
    is VoucherValue.Percent -> "−${percent.label}"
    is VoucherValue.Amount -> "−$amount $currency"
}

fun VoucherKind.label(): String = when (this) {
    VoucherKind.REUSABLE -> "Reusable"
    VoucherKind.SINGLE_USE -> "Single use"
}

fun parseVoucherKind(text: String): VoucherKind =
    VoucherKind.entries.firstOrNull { it.name == text } ?: throw ValidationException("Choose how often the voucher works", "kind")

fun parseVoucherValue(type: String, text: String): VoucherValue {
    val number = text.trim().removeSuffix("%").trim().replace(',', '.').toBigDecimalOrNull()
        ?: throw ValidationException("Value '$text' is not a number", "value")
    return when (type) {
        "PERCENT" -> VoucherValue.Percent(DiscountPercent.of(number))
        "AMOUNT" -> VoucherValue.Amount(Money.of(number))
        else -> throw ValidationException("Choose a percent or an amount", "valueType")
    }
}

fun parseValidUntil(text: String): LocalDate =
    try {
        LocalDate.parse(text.trim())
    } catch (e: DateTimeParseException) {
        throw ValidationException("Choose the last day the voucher works", "validUntil")
    }
