package com.passly.data.idempotency

import com.passly.application.idempotency.IdempotencyStore
import com.passly.application.idempotency.IdempotentRequest
import com.passly.application.idempotency.IdempotentResponse
import com.passly.application.idempotency.StoredIdempotency
import com.passly.domain.user.UserId
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant

@Repository
class JdbcIdempotencyStore(private val jdbc: JdbcClient) : IdempotencyStore {

    override fun claim(request: IdempotentRequest): Boolean =
        jdbc.sql(
            """
            insert into idempotency_key (user_id, idem_key, scope, fingerprint)
            values (:userId, :key, :scope, :fingerprint)
            on conflict do nothing
            """,
        )
            .param("userId", request.userId.value)
            .param("key", request.key)
            .param("scope", request.scope)
            .param("fingerprint", request.fingerprint)
            .update() == 1

    override fun find(userId: UserId, key: String): StoredIdempotency? =
        jdbc.sql(
            """
            select scope, fingerprint, response_status, response_body
            from idempotency_key
            where user_id = :userId and idem_key = :key
            """,
        )
            .param("userId", userId.value)
            .param("key", key)
            .query { rs, _ ->
                val status = (rs.getObject("response_status") as Number?)?.toInt()
                StoredIdempotency(
                    scope = rs.getString("scope"),
                    fingerprint = rs.getString("fingerprint"),
                    response = status?.let { IdempotentResponse(it, rs.getString("response_body")) },
                )
            }
            .optional()
            .orElse(null)

    override fun complete(request: IdempotentRequest, response: IdempotentResponse) {
        jdbc.sql(
            """
            update idempotency_key
            set response_status = :status, response_body = :body
            where user_id = :userId and idem_key = :key
            """,
        )
            .param("status", response.status)
            .param("body", response.body)
            .param("userId", request.userId.value)
            .param("key", request.key)
            .update()
    }

    override fun deleteCreatedBefore(moment: Instant): Int =
        jdbc.sql("delete from idempotency_key where created_at < :moment")
            .param("moment", Timestamp.from(moment))
            .update()
}
