package com.passly

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.kafka.KafkaContainer

@TestConfiguration(proxyBeanMethods = false)
class KafkaTestcontainersConfiguration {

    @Bean
    @ServiceConnection
    fun kafkaContainer() = KafkaContainer("apache/kafka:3.9.1")
}
