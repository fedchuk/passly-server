package com.passly.application.outbox

import com.passly.application.auth.MutableClock
import java.time.Instant
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class OutboxRelayTest {

    private val clock = MutableClock(Instant.parse("2026-10-20T10:00:00Z"))
    private val store = FakeOutboxStore()
    private val publisher = RecordingPublisher()
    private val relay = OutboxRelay(store, publisher, clock)

    private fun message(key: String) = OutboxMessage(UUID.randomUUID(), "menu", key, "MenuChanged", "{}")

    @Test
    fun `published messages are marked and not sent again`() {
        store.pending += listOf(message("a"), message("b"))

        assertEquals(2, relay.relayBatch(10))
        assertEquals(0, relay.relayBatch(10))

        assertEquals(listOf("a", "b"), publisher.sent.map { it.key })
        assertEquals(clock.instant(), store.published.values.distinct().single())
    }

    @Test
    fun `relay stops at the first failure to keep the order`() {
        store.pending += listOf(message("a"), message("broken"), message("c"))
        publisher.failOn = "broken"

        assertEquals(1, relay.relayBatch(10))

        assertEquals(listOf("broken", "c"), store.pending.map { it.key })

        publisher.failOn = null
        assertEquals(2, relay.relayBatch(10))
        assertEquals(listOf("a", "broken", "c"), publisher.sent.map { it.key })
    }

    private class FakeOutboxStore : OutboxStore {
        val pending = mutableListOf<OutboxMessage>()
        val published = linkedMapOf<UUID, Instant>()

        override fun lockUnpublished(limit: Int) = pending.take(limit)

        override fun markPublished(ids: Collection<UUID>, publishedAt: Instant) {
            ids.forEach { published[it] = publishedAt }
            pending.removeAll { it.id in ids }
        }
    }

    private class RecordingPublisher : OutboxPublisher {
        val sent = mutableListOf<OutboxMessage>()
        var failOn: String? = null

        override fun publish(message: OutboxMessage) {
            if (message.key == failOn) throw IllegalStateException("Broker is down")
            sent += message
        }
    }
}
