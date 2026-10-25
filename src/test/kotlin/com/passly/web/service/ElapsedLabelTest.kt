package com.passly.web.service

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

class ElapsedLabelTest {

    @Test
    fun `elapsed time reads like a waiter would say it`() {
        assertEquals("just now", Duration.ofSeconds(40).toElapsedLabel())
        assertEquals("35 min", Duration.ofMinutes(35).toElapsedLabel())
        assertEquals("1 h 05 min", Duration.ofMinutes(65).toElapsedLabel())
        assertEquals("just now", Duration.ofMinutes(-3).toElapsedLabel())
    }
}
