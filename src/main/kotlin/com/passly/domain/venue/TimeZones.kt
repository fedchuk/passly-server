package com.passly.domain.venue

import com.passly.domain.common.ValidationException
import java.time.DateTimeException
import java.time.ZoneId

object TimeZones {
    val DEFAULT: ZoneId = ZoneId.of("Europe/Paris")

    private val REGIONS = Regex("^(Africa|America|Antarctica|Asia|Atlantic|Australia|Europe|Indian|Pacific)/[A-Za-z_]+(/[A-Za-z_]+)?$")

    fun supported(): List<ZoneId> =
        ZoneId.getAvailableZoneIds().filter { REGIONS.matches(it) }.sorted().map(ZoneId::of)

    fun of(id: String): ZoneId {
        val trimmed = id.trim()
        if (!REGIONS.matches(trimmed)) throw ValidationException("Unknown time zone $id", "timeZone")
        return try {
            ZoneId.of(trimmed)
        } catch (e: DateTimeException) {
            throw ValidationException("Unknown time zone $id", "timeZone")
        }
    }
}
