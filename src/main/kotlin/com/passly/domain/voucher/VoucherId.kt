package com.passly.domain.voucher

import java.util.*

@JvmInline
value class VoucherId(val value: UUID) {
    override fun toString() = value.toString()

    companion object {
        fun new() = VoucherId(UUID.randomUUID())
    }
}
