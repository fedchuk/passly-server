package com.passly.application.idempotency

data class StoredIdempotency(
    val scope: String,
    val fingerprint: String,
    val response: IdempotentResponse?,
)
