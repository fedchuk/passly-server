package com.passly.data.order

import com.passly.data.common.BaseEntity
import jakarta.persistence.*
import org.hibernate.annotations.BatchSize
import java.math.BigDecimal
import java.time.Instant
import java.util.*

@Entity
@Table(name = "order_line")
class OrderLineEntity(
    id: UUID,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    val parentOrder: OrderEntity,

    @Column(name = "position", nullable = false, updatable = false)
    val position: Int,

    @Column(name = "round", nullable = false, updatable = false)
    val round: Int,

    @Column(name = "menu_item_id", nullable = false, updatable = false)
    val menuItemId: UUID,

    @Column(name = "name", nullable = false, updatable = false, length = 160)
    val name: String,

    @Column(name = "unit_price", nullable = false, updatable = false, precision = 10, scale = 2)
    val unitPrice: BigDecimal,

    @Column(name = "tax_rate", nullable = false, updatable = false, precision = 5, scale = 2)
    val taxRate: BigDecimal,

    @Column(name = "station_id", updatable = false)
    val stationId: UUID?,

    @Column(name = "quantity", nullable = false, updatable = false)
    val quantity: Int,

    @Column(name = "comment", updatable = false, length = 200)
    val comment: String?,

    @Column(name = "added_by", nullable = false, updatable = false)
    val addedBy: UUID,

    @Column(name = "added_at", nullable = false, updatable = false)
    val addedAt: Instant,
) : BaseEntity(id) {

    @ElementCollection
    @CollectionTable(name = "order_line_modifier", joinColumns = [JoinColumn(name = "line_id")])
    @OrderColumn(name = "position")
    @BatchSize(size = 50)
    var modifiers: MutableList<OrderLineModifierEmbeddable> = mutableListOf()
}
