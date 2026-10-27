package com.passly.data.menu

import com.passly.data.common.BaseEntity
import jakarta.persistence.*
import java.math.BigDecimal
import java.util.*

@Entity
@Table(name = "menu_item")
class MenuItemEntity(
    id: UUID,

    @Column(name = "tenant_id", nullable = false, updatable = false)
    val tenantId: UUID,

    @Column(name = "venue_id", nullable = false, updatable = false)
    val venueId: UUID,

    @Column(name = "category_id")
    var categoryId: UUID?,

    @Column(name = "name", nullable = false, length = 160)
    var name: String,

    @Column(name = "description", columnDefinition = "text")
    var description: String?,

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    var price: BigDecimal,

    @Column(name = "station_id")
    var stationId: UUID?,

    @Column(name = "tax_rate_id")
    var taxRateId: UUID?,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int,

    @Column(name = "available", nullable = false)
    var available: Boolean,

    @Column(name = "archived", nullable = false)
    var archived: Boolean,
) : BaseEntity(id) {

    @ElementCollection
    @CollectionTable(name = "menu_item_modifier_group", joinColumns = [JoinColumn(name = "menu_item_id")])
    @OrderColumn(name = "position")
    @Column(name = "group_id", nullable = false)
    var modifierGroupIds: MutableList<UUID> = mutableListOf()
}
