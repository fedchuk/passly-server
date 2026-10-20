package com.passly.application.outbox

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.*

@Service
class OutboxRelay(
    private val store: OutboxStore,
    private val publisher: OutboxPublisher,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(OutboxRelay::class.java)

    @Transactional
    fun relayBatch(limit: Int): Int {
        val published = mutableListOf<UUID>()
        for (message in store.lockUnpublished(limit)) {
            try {
                publisher.publish(message)
                published += message.id
            } catch (e: Exception) {
                log.warn("Outbox message {} was not published, will retry: {}", message.id, e.message)
                break
            }
        }
        if (published.isNotEmpty()) store.markPublished(published, clock.instant())
        return published.size
    }
}
