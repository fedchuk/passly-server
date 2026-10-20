package com.passly.messaging

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("passly.messaging")
data class MessagingProperties(
    val kafkaEnabled: Boolean = false,
    val menuTopic: String = "passly.menu-events",
    val relayIntervalMs: Long = 1000,
)
