package com.passly.domain.voucher

import com.passly.domain.common.ConflictException
import com.passly.domain.common.ValidationException
import com.passly.domain.order.OrderId
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId
import com.passly.domain.venue.validName
import java.time.Instant
import java.time.LocalDate

class Voucher private constructor(
    val id: VoucherId,
    val tenantId: TenantId,
    val venueId: VenueId,
    val code: VoucherCode,
    val name: String,
    val kind: VoucherKind,
    val value: VoucherValue,
    val validUntil: LocalDate,
    val issuedAt: Instant,
    redeemedOrderId: OrderId?,
    redeemedAt: Instant?,
    val version: Long?,
) {
    var redeemedOrderId: OrderId? = redeemedOrderId
        private set

    var redeemedAt: Instant? = redeemedAt
        private set

    val isNew: Boolean get() = version == null

    val isRedeemed: Boolean get() = redeemedOrderId != null

    fun isExpiredOn(date: LocalDate): Boolean = date.isAfter(validUntil)

    fun redeem(orderId: OrderId, at: Instant, today: LocalDate) {
        if (isExpiredOn(today)) throw ConflictException("Voucher ${code.label} expired on $validUntil")
        if (kind == VoucherKind.REUSABLE) return
        val current = redeemedOrderId
        if (current != null && current != orderId) throw ConflictException("Voucher ${code.label} was already used")
        redeemedOrderId = orderId
        redeemedAt = at
    }

    fun release(orderId: OrderId) {
        if (redeemedOrderId != orderId) return
        redeemedOrderId = null
        redeemedAt = null
    }

    fun requireDeletable() {
        if (isRedeemed) throw ConflictException("Voucher ${code.label} was used and stays for the records")
    }

    override fun equals(other: Any?) = other is Voucher && other.id == id

    override fun hashCode() = id.hashCode()

    override fun toString() = "Voucher(${code.label}, '$name', $kind, ${value.label}, until $validUntil)"

    companion object {
        const val MAX_NAME_LENGTH = 80

        fun create(
            tenantId: TenantId,
            venueId: VenueId,
            code: VoucherCode,
            name: String,
            kind: VoucherKind,
            value: VoucherValue,
            validUntil: LocalDate,
            today: LocalDate,
            issuedAt: Instant,
        ): Voucher {
            if (validUntil.isBefore(today)) throw ValidationException(
                "Valid until must be today or later",
                "validUntil"
            )
            return Voucher(
                VoucherId.new(), tenantId, venueId, code, validName(name, "Voucher", MAX_NAME_LENGTH),
                kind, value, validUntil, issuedAt, null, null, null,
            )
        }

        fun fromStorage(
            id: VoucherId,
            tenantId: TenantId,
            venueId: VenueId,
            code: VoucherCode,
            name: String,
            kind: VoucherKind,
            value: VoucherValue,
            validUntil: LocalDate,
            issuedAt: Instant,
            redeemedOrderId: OrderId?,
            redeemedAt: Instant?,
            version: Long,
        ) = Voucher(id, tenantId, venueId, code, name, kind, value, validUntil, issuedAt, redeemedOrderId, redeemedAt, version)
    }
}
