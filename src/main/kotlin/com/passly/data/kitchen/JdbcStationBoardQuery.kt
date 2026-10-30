package com.passly.data.kitchen

import com.passly.application.kitchen.StationBoardQuery
import com.passly.application.kitchen.StationTicket
import com.passly.application.kitchen.TicketLine
import com.passly.domain.menu.StationId
import com.passly.domain.order.OrderId
import com.passly.domain.order.OrderLineId
import com.passly.domain.order.PrepStatus
import com.passly.domain.venue.VenueId
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@Repository
class JdbcStationBoardQuery(private val jdbc: JdbcClient) : StationBoardQuery {

    override fun ticketsOf(venueId: VenueId, stationId: StationId, since: Instant): List<StationTicket> {
        val rows = jdbc.sql(LINES_SQL)
            .param("venueId", venueId.value)
            .param("stationId", stationId.value)
            .param("since", Timestamp.from(since))
            .query { rs, _ ->
                BoardRow(
                    orderId = rs.getObject("order_id", UUID::class.java),
                    tableLabel = rs.getString("table_label"),
                    round = rs.getInt("round"),
                    lineId = rs.getObject("line_id", UUID::class.java),
                    name = rs.getString("name"),
                    quantity = rs.getInt("quantity"),
                    comment = rs.getString("comment"),
                    status = PrepStatus.valueOf(rs.getString("prep_status")),
                    statusChangedAt = rs.getTimestamp("status_changed_at").toInstant(),
                    addedAt = rs.getTimestamp("added_at").toInstant(),
                    addedBy = rs.getString("added_by_name"),
                )
            }
            .list()
        if (rows.isEmpty()) return emptyList()
        val modifiers = modifiersOf(rows.map { it.lineId })
        return rows.groupBy { it.orderId to it.round }.values.map { lines ->
            val first = lines.first()
            StationTicket(
                orderId = OrderId(first.orderId),
                tableLabel = first.tableLabel,
                round = first.round,
                sentAt = lines.minOf { it.addedAt },
                sentBy = first.addedBy,
                lines = lines.map { row ->
                    TicketLine(
                        lineId = OrderLineId(row.lineId),
                        name = row.name,
                        quantity = row.quantity,
                        modifiers = modifiers[row.lineId].orEmpty(),
                        comment = row.comment,
                        status = row.status,
                        statusChangedAt = row.statusChangedAt,
                    )
                },
            )
        }
    }

    private fun modifiersOf(lineIds: List<UUID>): Map<UUID, List<String>> =
        jdbc.sql("select line_id, name from order_line_modifier where line_id in (:lineIds) order by line_id, position")
            .param("lineIds", lineIds)
            .query { rs, _ -> rs.getObject("line_id", UUID::class.java) to rs.getString("name") }
            .list()
            .groupBy({ it.first }, { it.second })

    private data class BoardRow(
        val orderId: UUID,
        val tableLabel: String,
        val round: Int,
        val lineId: UUID,
        val name: String,
        val quantity: Int,
        val comment: String?,
        val status: PrepStatus,
        val statusChangedAt: Instant,
        val addedAt: Instant,
        val addedBy: String,
    )

    private companion object {
        const val LINES_SQL = """
            select o.id as order_id, t.label as table_label, l.round, l.id as line_id, l.name, l.quantity, l.comment,
                   l.prep_status, l.status_changed_at, l.added_at, u.display_name as added_by_name
            from order_line l
                     join orders o on o.id = l.order_id
                     join dining_table t on t.id = o.table_id
                     join app_user u on u.id = l.added_by
            where o.venue_id = :venueId
              and l.station_id = :stationId
              and l.prep_status <> 'SERVED'
              and l.added_at >= :since
            order by l.added_at, o.id, l.round, l.position
        """
    }
}
