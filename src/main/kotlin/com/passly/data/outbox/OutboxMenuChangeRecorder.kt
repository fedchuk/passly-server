package com.passly.data.outbox

import com.passly.application.menu.MenuChangeKind
import com.passly.application.menu.MenuChangeRecorder
import com.passly.application.menu.MenuChangedEvent
import com.passly.domain.common.NotFoundException
import com.passly.domain.venue.VenueId
import com.passly.messaging.MessagingProperties
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.util.UUID

@Repository
class OutboxMenuChangeRecorder(
    private val jdbc: JdbcClient,
    private val json: JsonMapper,
    private val clock: Clock,
    private val messaging: MessagingProperties,
) : MenuChangeRecorder {

    override fun recordChange(venueId: VenueId, kind: MenuChangeKind) {
        val menuVersion = nextMenuVersion(venueId)
        val event = MenuChangedEvent(
            eventId = UUID.randomUUID().toString(),
            type = MenuChangedEvent.TYPE,
            venueId = venueId.value.toString(),
            menuVersion = menuVersion,
            change = kind,
            occurredAt = clock.instant().toString(),
        )
        jdbc.sql(
            """
            insert into outbox_event (id, aggregate_type, aggregate_id, event_type, topic, message_key, payload)
            values (:id, 'Venue', :venueId, :eventType, :topic, :key, cast(:payload as jsonb))
            """,
        )
            .param("id", UUID.fromString(event.eventId))
            .param("venueId", venueId.value)
            .param("eventType", MenuChangedEvent.TYPE)
            .param("topic", messaging.menuTopic)
            .param("key", event.venueId)
            .param("payload", json.writeValueAsString(event))
            .update()
    }

    private fun nextMenuVersion(venueId: VenueId): Long =
        jdbc.sql("update venue set menu_version = menu_version + 1 where id = :id returning menu_version")
            .param("id", venueId.value)
            .query { rs, _ -> rs.getLong("menu_version") }
            .optional()
            .orElseThrow { NotFoundException("Venue", venueId) }
}
