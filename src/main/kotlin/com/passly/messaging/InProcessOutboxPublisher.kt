package com.passly.messaging

import com.passly.application.menu.MenuChangeNotifier
import com.passly.application.menu.MenuChangedEvent
import com.passly.application.outbox.OutboxMessage
import com.passly.application.outbox.OutboxPublisher
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

@Component
@ConditionalOnBooleanProperty("passly.messaging.kafka-enabled", havingValue = false, matchIfMissing = true)
class InProcessOutboxPublisher(
    private val json: JsonMapper,
    private val notifier: MenuChangeNotifier,
) : OutboxPublisher {

    override fun publish(message: OutboxMessage) {
        if (message.eventType == MenuChangedEvent.TYPE) {
            notifier.menuChanged(json.readValue(message.payload, MenuChangedEvent::class.java))
        }
    }
}
