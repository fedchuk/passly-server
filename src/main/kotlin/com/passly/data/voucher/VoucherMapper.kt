package com.passly.data.voucher

import com.passly.domain.common.Money
import com.passly.domain.discount.DiscountPercent
import com.passly.domain.order.OrderId
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId
import com.passly.domain.voucher.Voucher
import com.passly.domain.voucher.VoucherCode
import com.passly.domain.voucher.VoucherId
import com.passly.domain.voucher.VoucherValue
import java.math.BigDecimal

fun VoucherEntity.toDomain() = Voucher.fromStorage(
    id = VoucherId(id),
    tenantId = TenantId(tenantId),
    venueId = VenueId(venueId),
    code = VoucherCode.fromStorage(code),
    name = name,
    kind = kind,
    value = voucherValueOf(percent, amount),
    validUntil = validUntil,
    issuedAt = issuedAt,
    redeemedOrderId = redeemedOrderId?.let(::OrderId),
    redeemedAt = redeemedAt,
    version = checkNotNull(version) { "Stored voucher $id has no version" },
)

fun Voucher.toNewEntity() = VoucherEntity(
    id = id.value,
    tenantId = tenantId.value,
    venueId = venueId.value,
    code = code.value,
    name = name,
    kind = kind,
    percent = (value as? VoucherValue.Percent)?.percent?.value,
    amount = (value as? VoucherValue.Amount)?.amount?.amount,
    validUntil = validUntil,
    issuedAt = issuedAt,
).also { it.updateFrom(this) }

fun VoucherEntity.updateFrom(voucher: Voucher) {
    redeemedOrderId = voucher.redeemedOrderId?.value
    redeemedAt = voucher.redeemedAt
}

fun voucherValueOf(percent: BigDecimal?, amount: BigDecimal?): VoucherValue = when {
    percent != null -> VoucherValue.Percent(DiscountPercent.of(percent))
    amount != null -> VoucherValue.Amount(Money.of(amount))
    else -> error("Voucher value has neither percent nor amount")
}
