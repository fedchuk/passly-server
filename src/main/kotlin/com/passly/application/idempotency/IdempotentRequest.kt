package com.passly.application.idempotency

import com.passly.domain.user.UserId

data class IdempotentRequest(
    val userId: UserId,
    val key: String,
    val scope: String,
    val fingerprint: String,
)
