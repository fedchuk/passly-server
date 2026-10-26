package com.passly.application.idempotency

data class IdempotentResult(
    val response: IdempotentResponse,
    val replayed: Boolean,
)
