package com.passly.application.floor

import com.passly.domain.floor.*
import com.passly.domain.venue.VenueId

class FakeFloorPlanRepository : FloorPlanRepository {
    val areas = linkedMapOf<DiningAreaId, DiningArea>()
    val tables = linkedMapOf<DiningTableId, DiningTable>()
    var lastSavedTables: Set<DiningTableId> = emptySet()

    override fun load(venueId: VenueId) = FloorPlan(
        venueId,
        areas.values.filter { it.venueId == venueId }.map(::copyOfArea),
        tables.values.filter { it.venueId == venueId }.map(::copyOfTable),
    )

    override fun saveAreas(areas: Collection<DiningArea>) {
        areas.forEach { this.areas[it.id] = copyOfArea(it) }
    }

    override fun saveTables(tables: Collection<DiningTable>) {
        tables.forEach { this.tables[it.id] = copyOfTable(it) }
        lastSavedTables = tables.map { it.id }.toSet()
    }

    override fun deleteArea(area: DiningArea) {
        areas.remove(area.id)
    }

    override fun deleteTable(table: DiningTable) {
        tables.remove(table.id)
    }

    private fun copyOfArea(a: DiningArea) =
        DiningArea.fromStorage(a.id, a.tenantId, a.venueId, a.name, a.sortOrder, a.version ?: 0)

    private fun copyOfTable(t: DiningTable) = DiningTable.fromStorage(
        t.id, t.tenantId, t.venueId, t.areaId, t.label, t.seats, t.sortOrder, t.active, t.version ?: 0,
    )
}

class FakeFloorChangeRecorder : FloorChangeRecorder {
    val changes = mutableListOf<VenueId>()

    override fun recordChange(venueId: VenueId) {
        changes += venueId
    }
}
