package com.passly.web.admin

import com.passly.domain.floor.DiningTable
import com.passly.domain.floor.FloorPlan
import java.util.*

data class AreaView(
    val id: UUID,
    val name: String,
    val version: Long?,
    val summary: String,
    val first: Boolean,
    val last: Boolean,
    val tables: List<TableTileView>,
)

data class TableTileView(
    val id: UUID,
    val label: String,
    val seatsLabel: String,
    val active: Boolean,
    val first: Boolean,
    val last: Boolean,
)

data class AreaOption(
    val id: UUID,
    val name: String,
)

fun FloorPlan.toAreaViews(): List<AreaView> {
    val ordered = areas
    return ordered.mapIndexed { index, area ->
        val tables = tablesIn(area.id)
        AreaView(
            id = area.id.value,
            name = area.name,
            version = area.version,
            summary = summaryOf(tables),
            first = index == 0,
            last = index == ordered.lastIndex,
            tables = tables.mapIndexed { position, table ->
                TableTileView(
                    id = table.id.value,
                    label = table.label,
                    seatsLabel = seatsLabel(table.seats),
                    active = table.active,
                    first = position == 0,
                    last = position == tables.lastIndex,
                )
            },
        )
    }
}

fun FloorPlan.areaOptions(): List<AreaOption> = areas.map { AreaOption(it.id.value, it.name) }

fun FloorPlan.nextTableNumber(): Int = (tables.mapNotNull { it.label.toIntOrNull() }.maxOrNull() ?: 0) + 1

private fun summaryOf(tables: List<DiningTable>): String {
    val active = tables.filter { it.active }
    val count = if (active.size == 1) "1 table" else "${active.size} tables"
    return "$count · ${seatsLabel(active.sumOf { it.seats })}"
}

private fun seatsLabel(seats: Int) = if (seats == 1) "1 seat" else "$seats seats"
