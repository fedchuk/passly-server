package com.passly.domain.menu

import com.passly.domain.common.Money

data class Modifier(
    val id: ModifierId,
    val name: String,
    val priceDelta: Money,
)

data class ModifierDraft(
    val name: String,
    val priceDelta: Money,
    val id: ModifierId? = null,
)
