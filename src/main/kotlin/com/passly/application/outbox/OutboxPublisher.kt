package com.passly.application.outbox

interface OutboxPublisher {
    fun publish(message: OutboxMessage)
}
