package com.passly.data.menu

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.math.BigDecimal
import java.util.UUID

@Embeddable
class ModifierEmbeddable(
    @Column(name = "id", nullable = false)
    var id: UUID,

    @Column(name = "name", nullable = false, length = 120)
    var name: String,

    @Column(name = "price_delta", nullable = false, precision = 10, scale = 2)
    var priceDelta: BigDecimal,
)
