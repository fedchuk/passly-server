package com.passly.messaging

import com.passly.application.outbox.OutboxMessage
import com.passly.application.outbox.OutboxPublisher
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
@ConditionalOnBooleanProperty("passly.messaging.kafka-enabled")
class KafkaOutboxPublisher(private val kafka: KafkaTemplate<String, String>) : OutboxPublisher {

    override fun publish(message: OutboxMessage) {
        val record = ProducerRecord(message.topic, message.key, message.payload)
        record.headers().add(EVENT_TYPE_HEADER, message.eventType.toByteArray())
        record.headers().add(EVENT_ID_HEADER, message.id.toString().toByteArray())
        kafka.send(record).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    private companion object {
        const val EVENT_TYPE_HEADER = "eventType"
        const val EVENT_ID_HEADER = "eventId"
        const val SEND_TIMEOUT_SECONDS = 10L
    }
}
