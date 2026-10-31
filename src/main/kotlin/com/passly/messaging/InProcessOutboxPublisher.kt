package com.passly.messaging

import com.passly.application.menu.MenuChangeNotifier
import com.passly.application.menu.MenuChangedEvent
import com.passly.application.order.OrderChangeNotifier
import com.passly.application.order.OrderChangedEvent
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
    private val orderNotifier: OrderChangeNotifier,
) : OutboxPublisher {

    override fun publish(message: OutboxMessage) {
        when (message.eventType) {
            MenuChangedEvent.TYPE -> notifier.menuChanged(json.readValue(message.payload, MenuChangedEvent::class.java))
            OrderChangedEvent.TYPE -> orderNotifier.orderChanged(json.readValue(message.payload, OrderChangedEvent::class.java))
        }
    }
}
