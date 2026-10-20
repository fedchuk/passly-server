package com.passly.data.venue

import com.passly.application.venue.VenueOverview
import com.passly.application.venue.VenueOverviewQuery
import com.passly.domain.venue.VenueId
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

@Repository
class JdbcVenueOverviewQuery(private val jdbc: JdbcClient) : VenueOverviewQuery {

    override fun overviewOf(venueId: VenueId): VenueOverview =
        jdbc.sql(OVERVIEW_SQL)
            .param("venueId", venueId.value)
            .query { rs, _ ->
                VenueOverview(
                    categories = rs.getInt("categories"),
                    items = rs.getInt("items"),
                    stopListed = rs.getInt("stop_listed"),
                    modifierGroups = rs.getInt("modifier_groups"),
                    staff = rs.getInt("staff"),
                )
            }
            .single()

    private companion object {
        const val OVERVIEW_SQL = """
            select
                (select count(*) from category where venue_id = :venueId) as categories,
                (select count(*) from menu_item where venue_id = :venueId and not archived) as items,
                (select count(*) from menu_item where venue_id = :venueId and not archived and not available) as stop_listed,
                (select count(*) from modifier_group where venue_id = :venueId) as modifier_groups,
                (select count(*) from membership m join app_user u on u.id = m.user_id
                  where m.venue_id = :venueId and u.enabled) as staff
        """
    }
}
