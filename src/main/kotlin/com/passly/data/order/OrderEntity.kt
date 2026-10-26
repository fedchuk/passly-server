package com.passly.data.order

import com.passly.data.common.BaseEntity
import com.passly.domain.order.OrderPaymentEntity
import com.passly.domain.order.OrderStatus
import jakarta.persistence.*
import org.hibernate.annotations.BatchSize
import java.time.Instant
import java.util.*

@Entity
@Table(name = "orders")
class OrderEntity(
    id: UUID,

    @Column(name = "tenant_id", nullable = false, updatable = false)
    val tenantId: UUID,

    @Column(name = "venue_id", nullable = false, updatable = false)
    val venueId: UUID,

    @Column(name = "table_id", nullable = false, updatable = false)
    val tableId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    var status: OrderStatus,

    @Column(name = "guests")
    var guests: Int?,

    @Column(name = "opened_by", nullable = false, updatable = false)
    val openedBy: UUID,

    @Column(name = "opened_at", nullable = false, updatable = false)
    val openedAt: Instant,
) : BaseEntity(id) {

    @Column(name = "closed_by")
    var closedBy: UUID? = null

    @Column(name = "closed_at")
    var closedAt: Instant? = null

    @OneToMany(mappedBy = "parentOrder", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("position")
    var lines: MutableSet<OrderLineEntity> = linkedSetOf()

    @OneToMany(mappedBy = "parentOrder", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("position")
    @BatchSize(size = 50)
    var payments: MutableSet<OrderPaymentEntity> = linkedSetOf()
}