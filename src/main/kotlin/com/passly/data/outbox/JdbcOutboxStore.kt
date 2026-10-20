package com.passly.data.outbox

import com.passly.application.outbox.OutboxMessage
import com.passly.application.outbox.OutboxStore
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.jvm.java

@Repository
class JdbcOutboxStore(private val jdbc: JdbcClient) : OutboxStore {

    override fun lockUnpublished(limit: Int): List<OutboxMessage> =
        jdbc.sql(
            """
            select id, topic, message_key, event_type, payload::text as payload
            from outbox_event
            where published_at is null
            order by position
            limit :limit
            for update skip locked
            """,
        )
            .param("limit", limit)
            .query { rs, _ ->
                OutboxMessage(
                    id = rs.getObject("id", UUID::class.java),
                    topic = rs.getString("topic"),
                    key = rs.getString("message_key"),
                    eventType = rs.getString("event_type"),
                    payload = rs.getString("payload"),
                )
            }
            .list()

    override fun markPublished(ids: Collection<UUID>, publishedAt: Instant) {
        jdbc.sql("update outbox_event set published_at = :publishedAt where id in (:ids)")
            .param("publishedAt", OffsetDateTime.ofInstant(publishedAt, ZoneOffset.UTC))
            .param("ids", ids)
            .update()
    }
}
