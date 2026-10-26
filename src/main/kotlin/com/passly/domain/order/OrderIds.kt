package com.passly.domain.order

import java.util.*

@JvmInline
value class OrderId(val value: UUID) {
    override fun toString() = value.toString()
    companion object { fun new() = OrderId(UUID.randomUUID()) }
}

@JvmInline
value class OrderLineId(val value: UUID) {
    override fun toString() = value.toString()
    companion object { fun new() = OrderLineId(UUID.randomUUID()) }
}

@JvmInline
value class PaymentId(val value: UUID) {
    override fun toString() = value.toString()
    companion object { fun new() = PaymentId(UUID.randomUUID()) }
}
