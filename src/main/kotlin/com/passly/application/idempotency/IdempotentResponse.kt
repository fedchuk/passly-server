package com.passly.application.idempotency

data class IdempotentResponse(
    val status: Int,
    val body: String,
)
