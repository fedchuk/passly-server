package com.passly.data.voucher

import com.passly.data.common.BaseEntity
import com.passly.domain.voucher.VoucherKind
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.*

@Entity
@Table(name = "voucher")
class VoucherEntity(
    id: UUID,

    @Column(name = "tenant_id", nullable = false, updatable = false)
    val tenantId: UUID,

    @Column(name = "venue_id", nullable = false, updatable = false)
    val venueId: UUID,

    @Column(name = "code", nullable = false, updatable = false, length = 8)
    val code: String,

    @Column(name = "name", nullable = false, updatable = false, length = 80)
    val name: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, updatable = false, length = 12)
    val kind: VoucherKind,

    @Column(name = "percent", updatable = false, precision = 5, scale = 2)
    val percent: BigDecimal?,

    @Column(name = "amount", updatable = false, precision = 10, scale = 2)
    val amount: BigDecimal?,

    @Column(name = "valid_until", nullable = false, updatable = false)
    val validUntil: LocalDate,

    @Column(name = "issued_at", nullable = false, updatable = false)
    val issuedAt: Instant,
) : BaseEntity(id) {

    @Column(name = "redeemed_order_id")
    var redeemedOrderId: UUID? = null

    @Column(name = "redeemed_at")
    var redeemedAt: Instant? = null
}
