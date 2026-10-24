package com.passly.data.order

import com.passly.data.common.upsert
import com.passly.domain.floor.DiningTableId
import com.passly.domain.order.Order
import com.passly.domain.order.OrderId
import com.passly.domain.order.OrderRepository
import com.passly.domain.order.OrderStatus
import com.passly.domain.venue.VenueId
import org.springframework.stereotype.Repository

@Repository
class OrderRepositoryImpl(
    private val jpa: OrderJpaRepository,
) : OrderRepository {

    override fun find(venueId: VenueId, id: OrderId): Order? =
        jpa.findByIdAndVenueId(id.value, venueId.value)?.toDomain()

    override fun findForUpdate(venueId: VenueId, id: OrderId): Order? =
        jpa.findByIdAndVenueIdForUpdate(id.value, venueId.value)?.toDomain()

    override fun findOpenAtTable(tableId: DiningTableId): Order? =
        jpa.findByTableIdAndStatus(tableId.value, OrderStatus.OPEN)?.toDomain()

    override fun findOpen(venueId: VenueId): List<Order> =
        jpa.findAllByVenueIdAndStatusOrderByOpenedAt(venueId.value, OrderStatus.OPEN).map { it.toDomain() }

    override fun existsAtTable(tableId: DiningTableId): Boolean =
        jpa.existsByTableId(tableId.value)

    override fun save(order: Order) {
        jpa.upsert(order.id.value, order.version, create = { order.toNewEntity() }, update = { updateFrom(order) })
    }
}
