package com.passly.domain.auth

import java.util.UUID

@JvmInline
value class RefreshTokenId(val value: UUID) {
    override fun toString() = value.toString()
    companion object { fun new() = RefreshTokenId(UUID.randomUUID()) }
}

@JvmInline
value class TokenFamilyId(val value: UUID) {
    override fun toString() = value.toString()
    companion object { fun new() = TokenFamilyId(UUID.randomUUID()) }
}
