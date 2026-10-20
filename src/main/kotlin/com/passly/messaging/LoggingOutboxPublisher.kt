package com.passly.messaging

import com.passly.application.outbox.OutboxMessage
import com.passly.application.outbox.OutboxPublisher
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnBooleanProperty("passly.messaging.kafka-enabled", havingValue = false, matchIfMissing = true)
class LoggingOutboxPublisher : OutboxPublisher {

    private val log = LoggerFactory.getLogger(LoggingOutboxPublisher::class.java)

    override fun publish(message: OutboxMessage) {
        log.info("Kafka is off, dropping {} {} for key {}", message.eventType, message.id, message.key)
    }
}
