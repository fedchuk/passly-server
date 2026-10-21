package com.passly.application.floor

import com.passly.TestcontainersConfiguration
import com.passly.domain.floor.DiningAreaId
import com.passly.domain.venue.VenueId
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class FloorPlanServiceIntegrationTest {

    @Autowired
    lateinit var service: FloorPlanService

    @Autowired
    lateinit var jdbc: JdbcClient

    private val tenantId = UUID.randomUUID()
    private val venueId = VenueId(UUID.randomUUID())

    @BeforeEach
    fun createVenue() {
        jdbc.sql("insert into tenant (id, name) values (?, ?)")
            .params(tenantId, "Test tenant")
            .update()
        insertVenue(venueId.value)
    }

    @Test
    fun `floor survives a round trip through the database`() {
        val hall = service.createArea(CreateAreaCommand(venueId, "Hall"))
        val terrace = service.createArea(CreateAreaCommand(venueId, "Terrace"))
        service.createTables(CreateTablesCommand(venueId, hall.id, 1..3, seats = 4))
        service.createTable(CreateTableCommand(venueId, terrace.id, "T1", 2))

        val plan = service.get(venueId)

        assertEquals(listOf("Hall", "Terrace"), plan.areas.map { it.name })
        assertEquals(listOf("1", "2", "3"), plan.tablesIn(hall.id).map { it.label })
        val t1 = plan.tablesIn(terrace.id).single()
        assertEquals(2, t1.seats)
        assertEquals(0L, t1.version)
    }

    @Test
    fun `every change bumps the floor version`() {
        val hall = service.createArea(CreateAreaCommand(venueId, "Hall"))
        service.createTables(CreateTablesCommand(venueId, hall.id, 1..2, seats = 4))

        assertEquals(2L, floorVersion())
    }

    @Test
    fun `moving a table between areas is persisted`() {
        val hall = service.createArea(CreateAreaCommand(venueId, "Hall"))
        val terrace = service.createArea(CreateAreaCommand(venueId, "Terrace"))
        val (one, two) = service.createTables(CreateTablesCommand(venueId, hall.id, 1..2, seats = 4))

        service.updateTable(UpdateTableCommand(venueId, one.id, terrace.id, "T1", seats = 2, active = true))

        val plan = service.get(venueId)
        assertEquals(listOf("T1"), plan.tablesIn(terrace.id).map { it.label })
        assertEquals(0, plan.table(two.id).sortOrder)
        assertEquals(1L, plan.table(one.id).version)
    }

    @Test
    fun `database rejects a duplicate label in another case`() {
        val hall = service.createArea(CreateAreaCommand(venueId, "Hall"))
        service.createTable(CreateTableCommand(venueId, hall.id, "A1", 4))

        assertFailsWith<DataIntegrityViolationException> { insertTable(hall.id, venueId.value, "a1") }
    }

    @Test
    fun `database rejects a table in an area of another venue`() {
        val otherVenue = UUID.randomUUID()
        insertVenue(otherVenue)
        val hall = service.createArea(CreateAreaCommand(venueId, "Hall"))

        assertFailsWith<DataIntegrityViolationException> { insertTable(hall.id, otherVenue, "1") }
    }

    private fun insertVenue(id: UUID) {
        jdbc.sql("insert into venue (id, tenant_id, name) values (?, ?, ?)")
            .params(id, tenantId, "Test venue")
            .update()
    }

    private fun insertTable(areaId: DiningAreaId, venue: UUID, label: String) {
        jdbc.sql("insert into dining_table (id, tenant_id, venue_id, area_id, label, seats) values (?, ?, ?, ?, ?, ?)")
            .params(UUID.randomUUID(), tenantId, venue, areaId.value, label, 4)
            .update()
    }

    private fun floorVersion(): Long =
        jdbc.sql("select floor_version from venue where id = :id")
            .param("id", venueId.value)
            .query { rs, _ -> rs.getLong(1) }
            .single()
}
