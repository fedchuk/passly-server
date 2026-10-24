package com.passly.data.order

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.math.BigDecimal
import java.util.*

@Embeddable
class OrderLineModifierEmbeddable(
    @Column(name = "modifier_id", nullable = false)
    var modifierId: UUID,

    @Column(name = "group_name", nullable = false, length = 120)
    var groupName: String,

    @Column(name = "name", nullable = false, length = 120)
    var name: String,

    @Column(name = "price_delta", nullable = false, precision = 10, scale = 2)
    var priceDelta: BigDecimal,
)
