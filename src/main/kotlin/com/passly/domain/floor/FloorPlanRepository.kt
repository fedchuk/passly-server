package com.passly.domain.floor

import com.passly.domain.venue.VenueId

interface FloorPlanRepository {
    fun load(venueId: VenueId): FloorPlan
    fun saveAreas(areas: Collection<DiningArea>)
    fun saveTables(tables: Collection<DiningTable>)
    fun deleteArea(area: DiningArea)
    fun deleteTable(table: DiningTable)
}
