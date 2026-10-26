package com.passly.data.venue

import com.passly.domain.venue.*
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

@Repository
class JdbcVenueSettingsRepository(private val jdbc: JdbcClient) : VenueSettingsRepository {

    override fun find(venueId: VenueId): VenueSettings? =
        jdbc.sql("select country, currency, version from venue where id = :id")
            .param("id", venueId.value)
            .query { rs, _ ->
                VenueSettings.fromStorage(
                    venueId = venueId,
                    country = CountryCode.of(rs.getString("country")),
                    currency = CurrencyCode.of(rs.getString("currency")),
                    version = rs.getLong("version"),
                )
            }
            .optional()
            .orElse(null)

    override fun save(settings: VenueSettings) {
        val updated = jdbc.sql(
            """
            update venue
            set country = :country, currency = :currency, version = version + 1, updated_at = now()
            where id = :id and version = :version
            """,
        )
            .param("country", settings.country.value)
            .param("currency", settings.currency.value)
            .param("id", settings.venueId.value)
            .param("version", settings.version)
            .update()
        if (updated == 0) throw OptimisticLockingFailureException("Venue ${settings.venueId} was modified by someone else")
    }
}
