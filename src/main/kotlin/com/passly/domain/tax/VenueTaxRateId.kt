package com.passly.domain.tax

import java.util.*

@JvmInline
value class VenueTaxRateId(val value: UUID) {
    override fun toString() = value.toString()

    companion object {
        fun new() = VenueTaxRateId(UUID.randomUUID())
    }
}
