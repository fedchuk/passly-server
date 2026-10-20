package com.passly.application.outbox

import java.util.*

data class OutboxMessage(
    val id: UUID,
    val topic: String,
    val key: String,
    val eventType: String,
    val payload: String,
)
