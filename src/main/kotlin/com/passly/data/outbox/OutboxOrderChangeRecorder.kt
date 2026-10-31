package com.passly.data.outbox

import com.passly.application.order.OrderChangeKind
import com.passly.application.order.OrderChangeRecorder
import com.passly.application.order.OrderChangedEvent
import com.passly.domain.menu.StationId
import com.passly.domain.order.Order
import com.passly.messaging.MessagingProperties
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.util.UUID
import kotlin.collections.map

@Repository
class OutboxOrderChangeRecorder(
    private val jdbc: JdbcClient,
    private val json: JsonMapper,
    private val clock: Clock,
    private val messaging: MessagingProperties,
) : OrderChangeRecorder {

    override fun recordChange(order: Order, kind: OrderChangeKind, stationIds: Collection<StationId>) {
        val event = OrderChangedEvent(
            eventId = UUID.randomUUID().toString(),
            type = OrderChangedEvent.TYPE,
            venueId = order.venueId.value.toString(),
            orderId = order.id.value.toString(),
            tableId = order.tableId.value.toString(),
            change = kind,
            stationIds = stationIds.map { it.value.toString() }.sorted(),
            occurredAt = clock.instant().toString(),
        )
        jdbc.sql(
            """
            insert into outbox_event (id, aggregate_type, aggregate_id, event_type, topic, message_key, payload)
            values (:id, 'Order', :orderId, :eventType, :topic, :key, cast(:payload as jsonb))
            """,
        )
            .param("id", UUID.fromString(event.eventId))
            .param("orderId", order.id.value)
            .param("eventType", OrderChangedEvent.TYPE)
            .param("topic", messaging.orderTopic)
            .param("key", event.venueId)
            .param("payload", json.writeValueAsString(event))
            .update()
    }
}
