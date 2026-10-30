package com.passly.domain.venue

import com.passly.domain.common.ValidationException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TimeZonesTest {

    @Test
    fun `only region time zones are offered`() {
        val supported = TimeZones.supported().map { it.id }

        assertTrue("Europe/Madrid" in supported)
        assertTrue("America/Argentina/Buenos_Aires" in supported)
        assertTrue(supported.none { it.startsWith("Etc/") || it == "UTC" })
        assertEquals(ZoneId.of("Europe/Madrid"), TimeZones.of(" Europe/Madrid "))
    }

    @Test
    fun `unknown and offset zones are refused`() {
        assertFailsWith<ValidationException> { TimeZones.of("Europe/Atlantis") }
        assertFailsWith<ValidationException> { TimeZones.of("UTC+02:00") }
        assertFailsWith<ValidationException> { TimeZones.of("") }
    }

    @Test
    fun `the day of the venue follows its time zone`() {
        val settings = VenueSettings.fromStorage(VenueId.new(), CountryCode.of("ES"), CurrencyCode.of("EUR"), 0)
        val lateEvening = Instant.parse("2026-10-30T23:30:00Z")

        assertEquals(LocalDate.parse("2026-10-31"), settings.today(lateEvening))

        settings.changeTimeZone(ZoneId.of("America/New_York"))

        assertEquals(LocalDate.parse("2026-10-30"), settings.today(lateEvening))
    }
}
