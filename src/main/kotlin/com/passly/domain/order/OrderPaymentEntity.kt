package com.passly.domain.order

import com.passly.data.common.BaseEntity
import com.passly.data.order.OrderEntity
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.*

@Entity
@Table(name = "order_payment")
class OrderPaymentEntity(
    id: UUID,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    val parentOrder: OrderEntity,

    @Column(name = "position", nullable = false, updatable = false)
    val position: Int,

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, updatable = false, length = 12)
    val method: PaymentMethod,

    @Column(name = "amount", nullable = false, updatable = false, precision = 10, scale = 2)
    val amount: BigDecimal,

    @Column(name = "taken_by", nullable = false, updatable = false)
    val takenBy: UUID,

    @Column(name = "taken_at", nullable = false, updatable = false)
    val takenAt: Instant,
) : BaseEntity(id)
