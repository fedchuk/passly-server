package com.passly.data.auth

import com.passly.data.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "refresh_token")
class RefreshTokenEntity(
    id: UUID,

    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: UUID,

    @Column(name = "family_id", nullable = false, updatable = false)
    val familyId: UUID,

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    val tokenHash: String,

    @Column(name = "expires_at", nullable = false, updatable = false)
    val expiresAt: Instant,

    @Column(name = "revoked_at")
    var revokedAt: Instant?,
) : BaseEntity(id)
