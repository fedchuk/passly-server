package com.passly.data.floor

import com.passly.data.common.upsert
import com.passly.domain.floor.DiningArea
import com.passly.domain.floor.DiningTable
import com.passly.domain.floor.FloorPlan
import com.passly.domain.floor.FloorPlanRepository
import com.passly.domain.venue.VenueId
import org.springframework.stereotype.Repository

@Repository
class FloorPlanRepositoryImpl(
    private val areaJpa: DiningAreaJpaRepository,
    private val tableJpa: DiningTableJpaRepository,
) : FloorPlanRepository {

    override fun load(venueId: VenueId): FloorPlan = FloorPlan(
        venueId = venueId,
        areas = areaJpa.findAllByVenueId(venueId.value).map { it.toDomain() },
        tables = tableJpa.findAllByVenueId(venueId.value).map { it.toDomain() },
    )

    override fun saveAreas(areas: Collection<DiningArea>) {
        areas.forEach { area ->
            areaJpa.upsert(area.id.value, area.version, create = { area.toNewEntity() }, update = { updateFrom(area) })
        }
    }

    override fun saveTables(tables: Collection<DiningTable>) {
        tables.forEach { table ->
            tableJpa.upsert(table.id.value, table.version, create = { table.toNewEntity() }, update = { updateFrom(table) })
        }
    }

    override fun deleteArea(area: DiningArea) {
        areaJpa.deleteById(area.id.value)
    }

    override fun deleteTable(table: DiningTable) {
        tableJpa.deleteById(table.id.value)
    }
}
