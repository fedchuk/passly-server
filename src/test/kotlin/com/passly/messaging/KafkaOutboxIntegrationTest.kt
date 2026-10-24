package com.passly.messaging

import com.passly.web.api.createStation
import com.passly.domain.menu.StationId
import com.passly.KafkaTestcontainersConfiguration
import com.passly.TestcontainersConfiguration
import com.passly.application.menu.CategoryService
import com.passly.application.menu.CreateCategoryCommand
import com.passly.domain.venue.VenueId
import com.passly.web.api.createVenue
import com.passly.web.api.menu.MenuEventStreams
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.testcontainers.kafka.KafkaContainer
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.test.DefaultAsserter.fail
import kotlin.test.Test
import kotlin.test.assertEquals

@SpringBootTest(
    properties = [
        "passly.messaging.kafka-enabled=true",
        "passly.messaging.relay-interval-ms=200",
        "spring.kafka.consumer.auto-offset-reset=earliest",
    ],
)
@Import(TestcontainersConfiguration::class, KafkaTestcontainersConfiguration::class)
class KafkaOutboxIntegrationTest {

    @Autowired
    lateinit var jdbc: JdbcClient

    @Autowired
    lateinit var categories: CategoryService

    @Autowired
    lateinit var kafka: KafkaContainer

    @Autowired
    lateinit var json: JsonMapper

    @Autowired
    lateinit var messaging: MessagingProperties

    @Autowired
    lateinit var streams: MenuEventStreams

    private fun consumer() = KafkaConsumer<String, String>(
        mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to "test-${UUID.randomUUID()}",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
        ),
    ).apply { subscribe(listOf(messaging.menuTopic)) }

    private fun KafkaConsumer<String, String>.awaitRecords(key: String, count: Int): List<ConsumerRecord<String, String>> {
        val found = mutableListOf<ConsumerRecord<String, String>>()
        val deadline = Instant.now().plusSeconds(30)
        while (found.size < count && Instant.now().isBefore(deadline)) {
            found += poll(Duration.ofMillis(500)).filter { it.key() == key }
        }
        if (found.size < count) fail("Expected $count events for $key, got ${found.size}")
        return found
    }

    @Test
    fun `menu changes reach kafka in order with the venue as the key`() {
        val venue = jdbc.createVenue()
        val venueId = VenueId(venue.venueId)
        val station = StationId(jdbc.createStation(venue))

        consumer().use { consumer ->
            categories.create(CreateCategoryCommand(venueId, "Wine", stationId = station))
            categories.create(CreateCategoryCommand(venueId, "Beer", stationId = station))

            val records = consumer.awaitRecords(venue.venueId.toString(), 2)

            val versions = records.map { json.readTree(it.value()).get("menuVersion").asLong() }
            assertEquals(listOf(1L, 2L), versions)
            assertEquals("MenuChanged", String(records.first().headers().lastHeader("eventType").value()))
        }
    }

    @Test
    fun `kafka listener passes changes to the open menu streams`() {
        val venue = jdbc.createVenue()
        val venueId = VenueId(venue.venueId)

        categories.create(CreateCategoryCommand(venueId, "Wine", stationId = StationId(jdbc.createStation(venue))))

        val deadline = Instant.now().plusSeconds(30)
        while (streams.knownVersion(venueId) != 1L) {
            if (Instant.now().isAfter(deadline)) fail("The Kafka listener did not deliver version 1")
            Thread.sleep(200)
        }
    }
}
