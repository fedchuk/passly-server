package com.passly.application.idempotency

import com.passly.domain.user.UserId
import java.time.Instant

class FakeIdempotencyStore : IdempotencyStore {
    private val stored = linkedMapOf<Pair<UserId, String>, StoredIdempotency>()

    override fun claim(request: IdempotentRequest): Boolean {
        val key = request.userId to request.key
        if (key in stored) return false
        stored[key] = StoredIdempotency(request.scope, request.fingerprint, null)
        return true
    }

    override fun find(userId: UserId, key: String) = stored[userId to key]

    override fun complete(request: IdempotentRequest, response: IdempotentResponse) {
        val key = request.userId to request.key
        stored[key] = stored.getValue(key).copy(response = response)
    }

    override fun deleteCreatedBefore(moment: Instant): Int {
        val count = stored.size
        stored.clear()
        return count
    }
}
