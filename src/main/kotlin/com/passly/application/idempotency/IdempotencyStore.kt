package com.passly.application.idempotency

import com.passly.domain.user.UserId
import java.time.Instant

interface IdempotencyStore {
    fun claim(request: IdempotentRequest): Boolean
    fun find(userId: UserId, key: String): StoredIdempotency?
    fun complete(request: IdempotentRequest, response: IdempotentResponse)
    fun deleteCreatedBefore(moment: Instant): Int
}
