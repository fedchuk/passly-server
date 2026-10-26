package com.passly.application.idempotency

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration

@Component
class IdempotencyCleanupJob(
    private val idempotency: IdempotencyService,
    private val clock: Clock,
) {

    @Scheduled(cron = "0 17 4 * * *")
    fun forgetOldKeys() {
        idempotency.forgetCreatedBefore(clock.instant().minus(KEEP_FOR))
    }

    private companion object {
        val KEEP_FOR: Duration = Duration.ofHours(24)
    }
}
