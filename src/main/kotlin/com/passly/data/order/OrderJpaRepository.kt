package com.passly.data.order

import com.passly.domain.order.OrderStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.*

interface OrderJpaRepository : JpaRepository<OrderEntity, UUID> {

    @EntityGraph(attributePaths = ["lines", "lines.modifiers"])
    fun findByIdAndVenueId(id: UUID, venueId: UUID): OrderEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderEntity o where o.id = :id and o.venueId = :venueId")
    fun findByIdAndVenueIdForUpdate(@Param("id") id: UUID, @Param("venueId") venueId: UUID): OrderEntity?

    @EntityGraph(attributePaths = ["lines", "lines.modifiers"])
    fun findByTableIdAndStatus(tableId: UUID, status: OrderStatus): OrderEntity?

    @EntityGraph(attributePaths = ["lines", "lines.modifiers"])
    fun findAllByVenueIdAndStatusOrderByOpenedAt(venueId: UUID, status: OrderStatus): List<OrderEntity>

    fun existsByTableId(tableId: UUID): Boolean
}
