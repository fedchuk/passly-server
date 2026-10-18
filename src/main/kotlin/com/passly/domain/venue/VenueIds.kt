package com.passly.domain.venue

import java.util.UUID

@JvmInline
value class TenantId(val value: UUID) {
    override fun toString() = value.toString()
    companion object { fun new() = TenantId(UUID.randomUUID()) }
}

@JvmInline
value class VenueId(val value: UUID) {
    override fun toString() = value.toString()
    companion object { fun new() = VenueId(UUID.randomUUID()) }
}
