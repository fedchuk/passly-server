package com.passly.domain.user

import java.util.UUID

@JvmInline
value class UserId(val value: UUID) {
    override fun toString() = value.toString()
    companion object { fun new() = UserId(UUID.randomUUID()) }
}
