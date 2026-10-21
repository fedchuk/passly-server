package com.passly.application.floor

import com.passly.domain.common.ConflictException
import com.passly.domain.common.NotFoundException
import com.passly.domain.floor.DiningArea
import com.passly.domain.floor.DiningAreaId
import com.passly.domain.floor.DiningTable
import com.passly.domain.floor.DiningTableId
import com.passly.domain.floor.FloorPlan
import com.passly.domain.floor.FloorPlanRepository
import com.passly.domain.venue.Venue
import com.passly.domain.venue.VenueId
import com.passly.domain.venue.VenueRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class FloorPlanService(
    private val venues: VenueRepository,
    private val plans: FloorPlanRepository,
    private val floorChanges: FloorChangeRecorder,
) {

    @Transactional(readOnly = true)
    fun get(venueId: VenueId): FloorPlan {
        requireVenue(venueId)
        return plans.load(venueId)
    }

    fun createArea(command: CreateAreaCommand): DiningArea {
        val venue = lockVenue(command.venueId)
        val area = plans.load(venue.id).addArea(venue.tenantId, command.name)
        plans.saveAreas(listOf(area))
        floorChanges.recordChange(venue.id)
        return area
    }

    fun renameArea(command: RenameAreaCommand): DiningArea {
        lockVenue(command.venueId)
        val plan = plans.load(command.venueId)
        val current = plan.area(command.areaId)
        requireVersion(current.version, command.expectedVersion, "Area '${current.name}'")
        val area = plan.renameArea(command.areaId, command.name)
        plans.saveAreas(listOf(area))
        floorChanges.recordChange(command.venueId)
        return area
    }

    fun shiftArea(venueId: VenueId, areaId: DiningAreaId, delta: Int) {
        lockVenue(venueId)
        val changed = plans.load(venueId).shiftArea(areaId, delta)
        if (changed.isEmpty()) return
        plans.saveAreas(changed)
        floorChanges.recordChange(venueId)
    }

    fun deleteArea(venueId: VenueId, areaId: DiningAreaId) {
        lockVenue(venueId)
        val plan = plans.load(venueId)
        val area = plan.area(areaId)
        val renumbered = plan.removeArea(areaId)
        plans.deleteArea(area)
        plans.saveAreas(renumbered)
        floorChanges.recordChange(venueId)
    }

    fun createTable(command: CreateTableCommand): DiningTable {
        val venue = lockVenue(command.venueId)
        val table = plans.load(venue.id).addTable(venue.tenantId, command.areaId, command.label, command.seats)
        plans.saveTables(listOf(table))
        floorChanges.recordChange(venue.id)
        return table
    }

    fun createTables(command: CreateTablesCommand): List<DiningTable> {
        val venue = lockVenue(command.venueId)
        val tables = plans.load(venue.id).addNumberedTables(
            tenantId = venue.tenantId,
            areaId = command.areaId,
            numbers = command.numbers,
            labelPrefix = command.labelPrefix,
            seats = command.seats,
        )
        plans.saveTables(tables)
        floorChanges.recordChange(venue.id)
        return tables
    }

    fun updateTable(command: UpdateTableCommand): DiningTable {
        lockVenue(command.venueId)
        val plan = plans.load(command.venueId)
        val current = plan.table(command.tableId)
        requireVersion(current.version, command.expectedVersion, "Table ${current.label}")
        val changed = plan.updateTable(command.tableId, command.areaId, command.label, command.seats, command.active)
        plans.saveTables(changed)
        floorChanges.recordChange(command.venueId)
        return plan.table(command.tableId)
    }

    fun shiftTable(venueId: VenueId, tableId: DiningTableId, delta: Int) {
        lockVenue(venueId)
        val changed = plans.load(venueId).shiftTable(tableId, delta)
        if (changed.isEmpty()) return
        plans.saveTables(changed)
        floorChanges.recordChange(venueId)
    }

    fun deleteTable(venueId: VenueId, tableId: DiningTableId) {
        lockVenue(venueId)
        val plan = plans.load(venueId)
        val table = plan.table(tableId)
        val renumbered = plan.removeTable(tableId)
        plans.deleteTable(table)
        plans.saveTables(renumbered)
        floorChanges.recordChange(venueId)
    }

    private fun requireVersion(actual: Long?, expected: Long?, what: String) {
        if (expected != null && expected != actual) {
            throw ConflictException("$what was changed by someone else. Reload and try again.")
        }
    }

    private fun requireVenue(id: VenueId): Venue =
        venues.findById(id) ?: throw NotFoundException("Venue", id)

    private fun lockVenue(id: VenueId): Venue =
        venues.findByIdAndLock(id) ?: throw NotFoundException("Venue", id)
}
