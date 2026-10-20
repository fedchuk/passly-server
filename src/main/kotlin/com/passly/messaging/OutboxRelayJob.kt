package com.passly.messaging

import com.passly.application.outbox.OutboxRelay
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class OutboxRelayJob(private val relay: OutboxRelay) {

    @Scheduled(fixedDelayString = "\${passly.messaging.relay-interval-ms:1000}")
    fun relayPendingMessages() {
        do {
            val published = relay.relayBatch(BATCH_SIZE)
        } while (published == BATCH_SIZE)
    }

    private companion object {
        const val BATCH_SIZE = 100
    }
}
