package com.passly.domain.role

import java.util.*

@JvmInline
value class VenueRoleId(val value: UUID) {
    override fun toString() = value.toString()

    companion object {
        fun new() = VenueRoleId(UUID.randomUUID())
    }
}
