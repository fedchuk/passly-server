package com.passly.domain.discount

import java.util.*

@JvmInline
value class VenueDiscountId(val value: UUID) {
    override fun toString() = value.toString()

    companion object {
        fun new() = VenueDiscountId(UUID.randomUUID())
    }
}
