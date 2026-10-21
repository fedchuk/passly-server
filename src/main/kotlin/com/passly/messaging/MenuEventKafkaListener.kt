package com.passly.messaging

import com.passly.application.menu.MenuChangeNotifier
import com.passly.application.menu.MenuChangedEvent
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

@Component
@ConditionalOnBooleanProperty("passly.messaging.kafka-enabled")
class MenuEventKafkaListener(
    private val json: JsonMapper,
    private val notifier: MenuChangeNotifier,
) {

    @KafkaListener(topics = ["\${passly.messaging.menu-topic}"], groupId = "passly-streams-\${random.uuid}")
    fun onMenuEvent(payload: String) {
        notifier.menuChanged(json.readValue(payload, MenuChangedEvent::class.java))
    }
}
