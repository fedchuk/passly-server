package com.passly.domain.order

import com.passly.domain.common.ConflictException
import com.passly.domain.common.Money
import com.passly.domain.common.ValidationException
import com.passly.domain.floor.DiningTable
import com.passly.domain.floor.DiningTableId
import com.passly.domain.menu.MenuItemId
import com.passly.domain.user.UserId
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId
import java.time.Instant

class Order private constructor(
    val id: OrderId,
    val tenantId: TenantId,
    val venueId: VenueId,
    val tableId: DiningTableId,
    status: OrderStatus,
    val guests: Int?,
    val openedBy: UserId,
    val openedAt: Instant,
    lines: List<OrderLine>,
    val version: Long?,
) {
    var status: OrderStatus = status
        private set

    var lines: List<OrderLine> = lines
        private set

    val isNew: Boolean get() = version == null

    val rounds: Int get() = lines.maxOfOrNull { it.round } ?: 0

    val total: Money get() = lines.fold(Money.ZERO) { sum, line -> sum + line.total }

    fun addRound(priced: List<PricedLine>, addedBy: UserId, addedAt: Instant): Int {
        if (status != OrderStatus.OPEN) throw ConflictException("Order $id is closed")
        if (priced.isEmpty()) throw ValidationException("A round needs at least one line", "lines")
        if (priced.size > MAX_LINES_PER_ROUND) {
            throw ValidationException("A round can have at most $MAX_LINES_PER_ROUND lines", "lines")
        }
        if (lines.size + priced.size > MAX_LINES) {
            throw ValidationException("An order can have at most $MAX_LINES lines", "lines")
        }
        val round = rounds + 1
        lines = lines + priced.map { line ->
            OrderLine(
                id = OrderLineId.new(),
                round = round,
                menuItemId = line.menuItemId,
                name = line.name,
                unitPrice = line.unitPrice,
                taxRate = line.taxRate,
                stationId = line.stationId,
                quantity = line.quantity,
                comment = line.comment,
                modifiers = line.modifiers,
                addedBy = addedBy,
                addedAt = addedAt,
            )
        }
        return round
    }

    override fun equals(other: Any?) = other is Order && other.id == id

    override fun hashCode() = id.hashCode()

    override fun toString() = "Order($id, table=$tableId, $status, ${lines.size} lines)"

    companion object {
        const val MAX_GUESTS = 50
        const val MAX_LINES_PER_ROUND = 100
        const val MAX_LINES = 500

        fun open(table: DiningTable, guests: Int?, openedBy: UserId, openedAt: Instant): Order {
            if (!table.active) throw ConflictException("Table ${table.label} is inactive")
            if (guests != null && guests !in 1..MAX_GUESTS) {
                throw ValidationException("Guests must be from 1 to $MAX_GUESTS", "guests")
            }
            return Order(
                id = OrderId.new(),
                tenantId = table.tenantId,
                venueId = table.venueId,
                tableId = table.id,
                status = OrderStatus.OPEN,
                guests = guests,
                openedBy = openedBy,
                openedAt = openedAt,
                lines = emptyList(),
                version = null,
            )
        }

        fun fromStorage(
            id: OrderId,
            tenantId: TenantId,
            venueId: VenueId,
            tableId: DiningTableId,
            status: OrderStatus,
            guests: Int?,
            openedBy: UserId,
            openedAt: Instant,
            lines: List<OrderLine>,
            version: Long,
        ) = Order(id, tenantId, venueId, tableId, status, guests, openedBy, openedAt, lines, version)
    }
}
