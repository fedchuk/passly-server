package com.passly.domain.floor

import java.util.*

@JvmInline
value class DiningAreaId(val value: UUID) {
    override fun toString() = value.toString()
    companion object { fun new() = DiningAreaId(UUID.randomUUID()) }
}

@JvmInline
value class DiningTableId(val value: UUID) {
    override fun toString() = value.toString()
    companion object { fun new() = DiningTableId(UUID.randomUUID()) }
}
