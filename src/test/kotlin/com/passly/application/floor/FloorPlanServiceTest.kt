package com.passly.application.floor

import com.passly.application.menu.FakeVenueRepository
import com.passly.domain.common.ConflictException
import com.passly.domain.common.NotFoundException
import com.passly.domain.floor.DiningArea
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.Venue
import com.passly.domain.venue.VenueId
import kotlin.test.*

class FloorPlanServiceTest {

    private val venue = Venue(VenueId.new(), TenantId.new(), "Bistro")

    private val venues = FakeVenueRepository(venue)
    private val plans = FakeFloorPlanRepository()
    private val floorChanges = FakeFloorChangeRecorder()
    private val service = FloorPlanService(venues, plans, floorChanges)

    private fun area(name: String): DiningArea = service.createArea(CreateAreaCommand(venue.id, name))

    @Test
    fun `new area is stored under the venue's tenant and changes the floor`() {
        val hall = area("Hall")

        assertEquals(venue.tenantId, plans.areas.getValue(hall.id).tenantId)
        assertEquals(listOf(venue.id), floorChanges.changes)
    }

    @Test
    fun `numbered tables are created as one change`() {
        val hall = area("Hall")
        floorChanges.changes.clear()

        val created = service.createTables(CreateTablesCommand(venue.id, hall.id, 1..20, seats = 4))

        assertEquals(20, created.size)
        assertEquals(20, plans.tables.size)
        assertEquals(1, floorChanges.changes.size)
    }

    @Test
    fun `a colliding batch stores nothing and keeps the floor version`() {
        val hall = area("Hall")
        service.createTables(CreateTablesCommand(venue.id, hall.id, 1..5, seats = 4))
        floorChanges.changes.clear()

        assertFailsWith<ConflictException> {
            service.createTables(CreateTablesCommand(venue.id, hall.id, 5..10, seats = 2))
        }

        assertEquals(5, plans.tables.size)
        assertTrue(floorChanges.changes.isEmpty())
    }

    @Test
    fun `table update with an outdated version is rejected`() {
        val hall = area("Hall")
        val table = service.createTable(CreateTableCommand(venue.id, hall.id, "1", 4))

        assertFailsWith<ConflictException> {
            service.updateTable(UpdateTableCommand(venue.id, table.id, hall.id, "10", 4, active = true, expectedVersion = 3))
        }
        assertEquals("1", plans.tables.getValue(table.id).label)
    }

    @Test
    fun `moving a table saves it and the renumbered tables of the old area`() {
        val hall = area("Hall")
        val terrace = area("Terrace")
        val (one, two, three) = service.createTables(CreateTablesCommand(venue.id, hall.id, 1..3, seats = 4))

        service.updateTable(UpdateTableCommand(venue.id, one.id, terrace.id, "1", seats = 2, active = false))

        assertEquals(setOf(one.id, two.id, three.id), plans.lastSavedTables)
        val moved = plans.tables.getValue(one.id)
        assertEquals(terrace.id, moved.areaId)
        assertEquals(2, moved.seats)
        assertFalse(moved.active)
        assertEquals(0, plans.tables.getValue(two.id).sortOrder)
    }

    @Test
    fun `an area with tables is not deleted`() {
        val hall = area("Hall")
        service.createTable(CreateTableCommand(venue.id, hall.id, "1", 4))

        assertFailsWith<ConflictException> { service.deleteArea(venue.id, hall.id) }
        assertTrue(hall.id in plans.areas)
    }

    @Test
    fun `deleting a table renumbers its area`() {
        val hall = area("Hall")
        val (one, two) = service.createTables(CreateTablesCommand(venue.id, hall.id, 1..2, seats = 4))

        service.deleteTable(venue.id, one.id)

        assertFalse(one.id in plans.tables)
        assertEquals(0, plans.tables.getValue(two.id).sortOrder)
    }

    @Test
    fun `a shift that changes nothing keeps the floor version`() {
        val hall = area("Hall")
        val terrace = area("Terrace")
        floorChanges.changes.clear()

        service.shiftArea(venue.id, hall.id, -1)
        assertTrue(floorChanges.changes.isEmpty())

        service.shiftArea(venue.id, terrace.id, -1)
        assertEquals(listOf("Terrace", "Hall"), service.get(venue.id).areas.map { it.name })
        assertEquals(1, floorChanges.changes.size)
    }

    @Test
    fun `every change locks the venue`() {
        val hall = area("Hall")
        venues.locks = 0

        val table = service.createTable(CreateTableCommand(venue.id, hall.id, "1", 4))
        service.shiftTable(venue.id, table.id, 1)
        service.deleteTable(venue.id, table.id)
        service.renameArea(RenameAreaCommand(venue.id, hall.id, "Main hall"))

        assertEquals(4, venues.locks)
    }

    @Test
    fun `unknown venue is not found`() {
        assertFailsWith<NotFoundException> { service.createArea(CreateAreaCommand(VenueId.new(), "Hall")) }
        assertFailsWith<NotFoundException> { service.get(VenueId.new()) }
    }
}
