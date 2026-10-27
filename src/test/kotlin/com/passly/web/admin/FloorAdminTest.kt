package com.passly.web.admin

import com.passly.TestcontainersConfiguration
import com.passly.application.floor.CreateAreaCommand
import com.passly.application.floor.CreateTableCommand
import com.passly.application.floor.CreateTablesCommand
import com.passly.application.floor.FloorPlanService
import com.passly.domain.floor.DiningArea
import com.passly.domain.venue.VenueId
import com.passly.web.api.StaffRole
import com.passly.web.api.TestVenue
import com.passly.web.api.createMember
import com.passly.web.api.createVenue
import com.passly.web.api.securedMockMvc
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.web.context.WebApplicationContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class FloorAdminTest {

    @Autowired
    lateinit var context: WebApplicationContext

    @Autowired
    lateinit var jdbc: JdbcClient

    @Autowired
    lateinit var userDetailsService: UserDetailsService

    @Autowired
    lateinit var floors: FloorPlanService

    lateinit var mockMvc: MockMvc
    lateinit var venue: TestVenue
    lateinit var manager: RequestPostProcessor

    private val venueId get() = VenueId(venue.venueId)
    private val base get() = "/admin/venues/${venue.venueId}/floor"

    @BeforeEach
    fun setUp() {
        mockMvc = context.securedMockMvc()
        venue = jdbc.createVenue()
        manager = member(StaffRole.MANAGER)
    }

    private fun member(role: StaffRole): RequestPostProcessor =
        user(userDetailsService.loadUserByUsername("${jdbc.createMember(venue, role)}@test.passly"))

    private fun area(name: String): DiningArea = floors.createArea(CreateAreaCommand(venueId, name))

    @Test
    fun `page shows areas with their tables and seats`() {
        val hall = area("Main hall")
        floors.createTables(CreateTablesCommand(venueId, hall.id, 1..2, labelPrefix = "T", seats = 3))

        mockMvc.get(base) { with(manager) }.andExpect {
            status { isOk() }
            content { string(containsString("Main hall")) }
            content { string(containsString("T2")) }
            content { string(containsString("2 tables · 6 seats")) }
        }
    }

    @Test
    fun `venue sidebar links to the tables page`() {
        mockMvc.get("/admin/venues/${venue.venueId}") { with(manager) }.andExpect {
            status { isOk() }
            content { string(containsString("href=\"$base\"")) }
            content { string(containsString("Modifier groups")) }
        }
    }

    @Test
    fun `new area is added from the inline form`() {
        mockMvc.post("$base/areas") {
            with(manager)
            with(csrf())
            param("name", "Terrace")
        }.andExpect {
            redirectedUrl(base)
            flash { attribute("message", "Area 'Terrace' added") }
        }

        assertEquals(listOf("Terrace"), floors.get(venueId).areas.map { it.name })
    }

    @Test
    fun `numbered tables are added from the form`() {
        val hall = area("Main hall")

        mockMvc.post("$base/areas/${hall.id}/numbered-tables") {
            with(manager)
            with(csrf())
            param("labelPrefix", "")
            param("from", "1")
            param("to", "20")
            param("seats", "4")
        }.andExpect {
            redirectedUrl(base)
            flash { attribute("message", "Tables 1–20 added") }
        }

        assertEquals(20, floors.get(venueId).tablesIn(hall.id).size)
    }

    @Test
    fun `a colliding label is reported and nothing is added`() {
        val hall = area("Main hall")
        floors.createTable(CreateTableCommand(venueId, hall.id, "3", 4))

        mockMvc.post("$base/areas/${hall.id}/numbered-tables") {
            with(manager)
            with(csrf())
            param("from", "1")
            param("to", "5")
            param("seats", "4")
        }.andExpect {
            redirectedUrl(base)
            flash { attribute("error", "Table 3 already exists") }
        }

        assertEquals(1, floors.get(venueId).tables.size)
    }

    @Test
    fun `a number that is not a number gets a readable message`() {
        val hall = area("Main hall")

        mockMvc.post("$base/areas/${hall.id}/numbered-tables") {
            with(manager)
            with(csrf())
            param("from", "one")
            param("to", "5")
            param("seats", "4")
        }.andExpect {
            redirectedUrl(base)
            flash { attribute("error", "Enter a whole number") }
        }
    }

    @Test
    fun `edit page shows the table and the areas to choose from`() {
        val hall = area("Main hall")
        area("Terrace")
        val table = floors.createTable(CreateTableCommand(venueId, hall.id, "7", 4))

        mockMvc.get("$base/tables/${table.id}/edit") { with(manager) }.andExpect {
            status { isOk() }
            content { string(containsString("Table 7")) }
            content { string(containsString("Terrace")) }
        }
    }

    @Test
    fun `editing moves the table to another area and deactivates it`() {
        val hall = area("Main hall")
        val terrace = area("Terrace")
        val table = floors.createTable(CreateTableCommand(venueId, hall.id, "7", 4))

        mockMvc.post("$base/tables/${table.id}") {
            with(manager)
            with(csrf())
            param("label", "T7")
            param("seats", "2")
            param("areaId", terrace.id.toString())
            param("_active", "on")
            param("version", "0")
        }.andExpect {
            redirectedUrl(base)
            flash { attribute("message", "Table T7 saved") }
        }

        val saved = floors.get(venueId).table(table.id)
        assertEquals(terrace.id, saved.areaId)
        assertEquals("T7", saved.label)
        assertFalse(saved.active)
    }

    @Test
    fun `invalid seats show the form again next to the field`() {
        val hall = area("Main hall")
        val table = floors.createTable(CreateTableCommand(venueId, hall.id, "7", 4))

        mockMvc.post("$base/tables/${table.id}") {
            with(manager)
            with(csrf())
            param("label", "7")
            param("seats", "0")
            param("areaId", hall.id.toString())
            param("active", "true")
            param("version", "0")
        }.andExpect {
            status { isOk() }
            content { string(containsString("Seats must be from 1 to 50")) }
        }

        assertEquals(4, floors.get(venueId).table(table.id).seats)
    }

    @Test
    fun `an area with tables is not deleted`() {
        val hall = area("Main hall")
        floors.createTable(CreateTableCommand(venueId, hall.id, "1", 4))

        mockMvc.post("$base/areas/${hall.id}/delete") {
            with(manager)
            with(csrf())
        }.andExpect {
            redirectedUrl(base)
            flash { attribute("error", "Area 'Main hall' has tables. Move or delete them first.") }
        }

        assertTrue(floors.get(venueId).areas.any { it.id == hall.id })
    }

    @Test
    fun `waiter cannot open the tables page`() {
        mockMvc.get(base) { with(member(StaffRole.WAITER)) }.andExpect { status { isForbidden() } }
    }
}
