package com.passly.web.api

import com.passly.application.idempotency.IdempotencyService
import com.passly.application.idempotency.IdempotentRequest
import com.passly.application.idempotency.IdempotentResponse
import com.passly.domain.user.UserId
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.security.MessageDigest
import java.util.*

@Component
class IdempotentResponses(
    private val idempotency: IdempotencyService,
    private val json: JsonMapper,
) {

    fun <T : Any> respond(
        userId: UserId,
        key: String,
        scope: String,
        request: Any,
        status: HttpStatus,
        type: Class<T>,
        action: () -> T,
    ): ResponseEntity<T> {
        val result = idempotency.execute(IdempotentRequest(userId, key, scope, fingerprintOf(request))) {
            IdempotentResponse(status.value(), json.writeValueAsString(action()))
        }
        return ResponseEntity.status(result.response.status)
            .header(REPLAYED_HEADER, result.replayed.toString())
            .body(json.readValue(result.response.body, type))
    }

    private fun fingerprintOf(request: Any): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(request)))

    companion object {
        const val KEY_HEADER = "Idempotency-Key"
        const val REPLAYED_HEADER = "Idempotent-Replayed"
    }
}
