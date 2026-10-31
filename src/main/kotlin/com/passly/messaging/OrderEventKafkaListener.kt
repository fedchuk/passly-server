package com.passly.messaging

import com.passly.application.order.OrderChangeNotifier
import com.passly.application.order.OrderChangedEvent
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

@Component
@ConditionalOnBooleanProperty("passly.messaging.kafka-enabled")
class OrderEventKafkaListener(
    private val json: JsonMapper,
    private val notifier: OrderChangeNotifier,
) {

    @KafkaListener(topics = ["\${passly.messaging.order-topic}"], groupId = "passly-order-streams-\${random.uuid}")
    fun onOrderEvent(payload: String) {
        notifier.orderChanged(json.readValue(payload, OrderChangedEvent::class.java))
    }
}
