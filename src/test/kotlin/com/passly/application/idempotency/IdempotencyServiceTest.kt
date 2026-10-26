package com.passly.application.idempotency

import com.passly.domain.common.ValidationException
import com.passly.domain.user.UserId
import java.time.Instant
import kotlin.test.*

class IdempotencyServiceTest {

    private val store = FakeIdempotencyStore()
    private val service = IdempotencyService(store)
    private val waiter = UserId.new()
    private var calls = 0

    private fun request(key: String = "key-1", fingerprint: String = "abc") =
        IdempotentRequest(waiter, key, "payment:order-1", fingerprint)

    private fun action(): IdempotentResponse {
        calls++
        return IdempotentResponse(200, """{"call":$calls}""")
    }

    @Test
    fun `first request runs, the retry replays the stored response`() {
        val first = service.execute(request(), ::action)
        val retry = service.execute(request(), ::action)

        assertFalse(first.replayed)
        assertTrue(retry.replayed)
        assertEquals(first.response, retry.response)
        assertEquals(1, calls)
    }

    @Test
    fun `another request with the same key is rejected`() {
        service.execute(request(), ::action)

        assertFailsWith<IdempotencyKeyReusedException> { service.execute(request(fingerprint = "other"), ::action) }
        assertEquals(1, calls)
    }

    @Test
    fun `keys are per user and validated`() {
        service.execute(request(), ::action)
        service.execute(IdempotentRequest(UserId.new(), "key-1", "payment:order-1", "abc"), ::action)

        assertEquals(2, calls)
        assertFailsWith<ValidationException> { service.execute(request(key = " "), ::action) }
        assertFailsWith<ValidationException> { service.execute(request(key = "k".repeat(256)), ::action) }
    }

    @Test
    fun `old keys are forgotten`() {
        service.execute(request(), ::action)

        assertEquals(1, service.forgetCreatedBefore(Instant.MAX))
        assertFalse(service.execute(request(), ::action).replayed)
    }
}
