package com.passly.messaging

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder

@Configuration
@ConditionalOnBooleanProperty("passly.messaging.kafka-enabled")
class KafkaTopicConfig {

    @Bean
    fun menuEventsTopic(messaging: MessagingProperties): NewTopic =
        TopicBuilder.name(messaging.menuTopic).partitions(MENU_TOPIC_PARTITIONS).replicas(1).build()

    @Bean
    fun orderEventsTopic(messaging: MessagingProperties): NewTopic =
        TopicBuilder.name(messaging.orderTopic).partitions(ORDER_TOPIC_PARTITIONS).replicas(1).build()

    private companion object {
        const val MENU_TOPIC_PARTITIONS = 2
        const val ORDER_TOPIC_PARTITIONS = 2
    }
}
