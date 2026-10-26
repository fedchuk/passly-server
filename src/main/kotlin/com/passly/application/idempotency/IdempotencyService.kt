package com.passly.application.idempotency

import com.passly.domain.common.ConflictException
import com.passly.domain.common.ValidationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class IdempotencyService(private val store: IdempotencyStore) {

    @Transactional
    fun execute(request: IdempotentRequest, action: () -> IdempotentResponse): IdempotentResult {
        if (request.key.isBlank() || request.key.length > MAX_KEY_LENGTH) {
            throw ValidationException("Idempotency-Key must be from 1 to $MAX_KEY_LENGTH characters", "Idempotency-Key")
        }
        if (store.claim(request)) {
            val response = action()
            store.complete(request, response)
            return IdempotentResult(response, replayed = false)
        }
        val stored = checkNotNull(store.find(request.userId, request.key)) { "Claimed key ${request.key} is missing" }
        if (stored.scope != request.scope || stored.fingerprint != request.fingerprint) throw IdempotencyKeyReusedException()
        val response = stored.response ?: throw ConflictException("A request with this Idempotency-Key is still running")
        return IdempotentResult(response, replayed = true)
    }

    @Transactional
    fun forgetCreatedBefore(moment: Instant): Int = store.deleteCreatedBefore(moment)

    companion object {
        const val MAX_KEY_LENGTH = 255
    }
}
