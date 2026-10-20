package com.passly.application.outbox

import java.time.Instant
import java.util.*

interface OutboxStore {
    fun lockUnpublished(limit: Int): List<OutboxMessage>
    fun markPublished(ids: Collection<UUID>, publishedAt: Instant)
}
