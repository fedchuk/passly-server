package com.passly.domain.floor

import com.passly.domain.common.ConflictException
import com.passly.domain.common.NotFoundException
import com.passly.domain.common.ValidationException
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId
import kotlin.test.*

class FloorPlanTest {

    private val tenant = TenantId.new()
    private val plan = FloorPlan(VenueId.new(), emptyList(), emptyList())

    private fun labelsIn(area: DiningArea) = plan.tablesIn(area.id).map { it.label }

    @Test
    fun `areas are appended in order and their names are unique ignoring case`() {
        plan.addArea(tenant, "Hall")
        val terrace = plan.addArea(tenant, " Terrace ")

        assertEquals(listOf("Hall", "Terrace"), plan.areas.map { it.name })
        assertEquals(1, terrace.sortOrder)
        assertFailsWith<ConflictException> { plan.addArea(tenant, "hall") }
        assertFailsWith<ValidationException> { plan.addArea(tenant, "  ") }
    }

    @Test
    fun `an area can be renamed to its own name in another case`() {
        val hall = plan.addArea(tenant, "Hall")
        plan.addArea(tenant, "Terrace")

        assertEquals("HALL", plan.renameArea(hall.id, "HALL").name)
        assertFailsWith<ConflictException> { plan.renameArea(hall.id, "terrace") }
    }

    @Test
    fun `numbered tables get labels and positions in order`() {
        val hall = plan.addArea(tenant, "Hall")

        val tables = plan.addNumberedTables(tenant, hall.id, 1..20, labelPrefix = "", seats = 4)

        assertEquals((1..20).map { it.toString() }, labelsIn(hall))
        assertEquals((0..19).toList(), plan.tablesIn(hall.id).map { it.sortOrder })
        assertTrue(tables.all { it.isNew && it.active && it.seats == 4 })
    }

    @Test
    fun `prefix is put in front of every number`() {
        val terrace = plan.addArea(tenant, "Terrace")

        plan.addNumberedTables(tenant, terrace.id, 1..3, labelPrefix = "T", seats = 2)

        assertEquals(listOf("T1", "T2", "T3"), labelsIn(terrace))
    }

    @Test
    fun `labels are unique in the whole venue ignoring case`() {
        val hall = plan.addArea(tenant, "Hall")
        val terrace = plan.addArea(tenant, "Terrace")
        plan.addTable(tenant, hall.id, "T1", 4)

        assertFailsWith<ConflictException> { plan.addTable(tenant, terrace.id, "t1", 2) }
    }

    @Test
    fun `a batch that collides with existing labels adds nothing`() {
        val hall = plan.addArea(tenant, "Hall")
        plan.addNumberedTables(tenant, hall.id, 1..5, labelPrefix = "", seats = 4)

        val error = assertFailsWith<ConflictException> {
            plan.addNumberedTables(tenant, hall.id, 4..8, labelPrefix = "", seats = 4)
        }

        assertEquals("Tables 4, 5 already exist", error.message)
        assertEquals(5, plan.tables.size)
    }

    @Test
    fun `a batch must start at 1 and stay within the limit`() {
        val hall = plan.addArea(tenant, "Hall")

        assertFailsWith<ValidationException> { plan.addNumberedTables(tenant, hall.id, 0..3, "", 4) }
        assertFailsWith<ValidationException> { plan.addNumberedTables(tenant, hall.id, 5..3, "", 4) }
        assertFailsWith<ValidationException> {
            plan.addNumberedTables(tenant, hall.id, 1..FloorPlan.MAX_BATCH + 1, "", 4)
        }
        assertTrue(plan.tables.isEmpty())
    }

    @Test
    fun `label and seats are validated`() {
        val hall = plan.addArea(tenant, "Hall")

        assertEquals("label", assertFailsWith<ValidationException> { plan.addTable(tenant, hall.id, " ", 4) }.field)
        assertFailsWith<ValidationException> { plan.addTable(tenant, hall.id, "x".repeat(DiningTable.MAX_LABEL_LENGTH + 1), 4) }
        assertEquals("seats", assertFailsWith<ValidationException> { plan.addTable(tenant, hall.id, "1", 0) }.field)
        assertFailsWith<ValidationException> { plan.addTable(tenant, hall.id, "1", DiningTable.MAX_SEATS + 1) }
    }

    @Test
    fun `moving a table appends it to the new area and closes the gap in the old one`() {
        val hall = plan.addArea(tenant, "Hall")
        val terrace = plan.addArea(tenant, "Terrace")
        val (_, two, three) = plan.addNumberedTables(tenant, hall.id, 1..3, "", 4)
        plan.addTable(tenant, terrace.id, "T1", 2)

        val changed = plan.updateTable(two.id, terrace.id, "2", seats = 2, active = false)

        assertEquals(listOf("T1", "2"), labelsIn(terrace))
        assertEquals(listOf("1", "3"), labelsIn(hall))
        assertEquals(1, three.sortOrder)
        assertEquals(setOf(two, three), changed)
        assertFalse(two.active)
    }

    @Test
    fun `a table keeps its label when only the seats change`() {
        val hall = plan.addArea(tenant, "Hall")
        val table = plan.addTable(tenant, hall.id, "1", 4)

        assertEquals(setOf(table), plan.updateTable(table.id, hall.id, "1", seats = 6, active = true))
        assertEquals(6, table.seats)
    }

    @Test
    fun `shifting a table moves it past its neighbour and stops at the edge`() {
        val hall = plan.addArea(tenant, "Hall")
        val (one, _, three) = plan.addNumberedTables(tenant, hall.id, 1..3, "", 4)

        plan.shiftTable(three.id, -1)

        assertEquals(listOf("1", "3", "2"), labelsIn(hall))
        assertTrue(plan.shiftTable(one.id, -5).isEmpty())
    }

    @Test
    fun `an area with tables cannot be removed`() {
        val hall = plan.addArea(tenant, "Hall")
        plan.addTable(tenant, hall.id, "1", 4)

        assertFailsWith<ConflictException> { plan.removeArea(hall.id) }
        assertEquals(listOf(hall), plan.areas)
    }

    @Test
    fun `removing an area renumbers the rest`() {
        val hall = plan.addArea(tenant, "Hall")
        val terrace = plan.addArea(tenant, "Terrace")

        assertEquals(setOf(terrace), plan.removeArea(hall.id))
        assertEquals(0, terrace.sortOrder)
    }

    @Test
    fun `unknown area and table are not found`() {
        assertFailsWith<NotFoundException> { plan.addTable(tenant, DiningAreaId.new(), "1", 4) }
        assertFailsWith<NotFoundException> { plan.removeTable(DiningTableId.new()) }
    }
}
