package com.passly.data.venue

import com.passly.domain.venue.*
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp

@Repository
class JdbcVenueSettingsRepository(private val jdbc: JdbcClient) : VenueSettingsRepository {

    override fun find(venueId: VenueId): VenueSettings? {
        val accepted = acceptedCurrencies(venueId)
        return jdbc.sql("select country, currency, version from venue where id = :id")
            .param("id", venueId.value)
            .query { rs, _ ->
                VenueSettings.fromStorage(
                    venueId = venueId,
                    country = CountryCode.of(rs.getString("country")),
                    currency = CurrencyCode.of(rs.getString("currency")),
                    version = rs.getLong("version"),
                    acceptedCurrencies = accepted,
                )
            }
            .optional()
            .orElse(null)
    }

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
        replaceAcceptedCurrencies(settings)
    }

    private fun acceptedCurrencies(venueId: VenueId): List<AcceptedCurrency> =
        jdbc.sql("select currency, rate, rate_set_at from venue_accepted_currency where venue_id = :id order by position")
            .param("id", venueId.value)
            .query { rs, _ ->
                AcceptedCurrency(
                    currency = CurrencyCode.of(rs.getString("currency")),
                    rate = ExchangeRate.of(rs.getBigDecimal("rate")),
                    rateSetAt = rs.getTimestamp("rate_set_at").toInstant(),
                )
            }
            .list()

    private fun replaceAcceptedCurrencies(settings: VenueSettings) {
        jdbc.sql("delete from venue_accepted_currency where venue_id = :id")
            .param("id", settings.venueId.value)
            .update()
        settings.acceptedCurrencies.forEachIndexed { position, accepted ->
            jdbc.sql(
                """
                insert into venue_accepted_currency (venue_id, currency, position, rate, rate_set_at)
                values (:id, :currency, :position, :rate, :rateSetAt)
                """,
            )
                .param("id", settings.venueId.value)
                .param("currency", accepted.currency.value)
                .param("position", position)
                .param("rate", accepted.rate.value)
                .param("rateSetAt", Timestamp.from(accepted.rateSetAt))
                .update()
        }
    }
}
