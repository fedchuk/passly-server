package com.passly.data.venue

import com.passly.application.platform.BusinessDirectoryQuery
import com.passly.application.platform.BusinessSummary
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.*

@Repository
class JdbcBusinessDirectoryQuery(private val jdbc: JdbcClient) : BusinessDirectoryQuery {

    override fun allBusinesses(): List<BusinessSummary> =
        jdbc.sql(DIRECTORY_SQL)
            .query { rs, _ ->
                BusinessSummary(
                    id = rs.getObject("id", UUID::class.java),
                    name = rs.getString("name"),
                    venues = rs.getInt("venues"),
                    owners = rs.getString("owners") ?: "",
                    createdOn = rs.getObject("created_on", LocalDate::class.java),
                )
            }
            .list()

    private companion object {
        const val DIRECTORY_SQL = """
            select
                t.id,
                t.name,
                t.created_at::date as created_on,
                (select count(*) from venue v where v.tenant_id = t.id) as venues,
                (select string_agg(u.email, ', ' order by u.email)
                   from membership m join app_user u on u.id = m.user_id
                  where m.tenant_id = t.id and m.venue_id is null) as owners
            from tenant t
            order by t.created_at desc, t.name
        """
    }
}
