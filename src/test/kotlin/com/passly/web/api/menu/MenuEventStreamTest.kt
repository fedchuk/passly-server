package com.passly.web.api.menu

import com.passly.web.api.createStation
import com.passly.domain.menu.StationId
import com.passly.TestcontainersConfiguration
import com.passly.application.menu.CategoryService
import com.passly.application.menu.CreateCategoryCommand
import com.passly.application.menu.MenuChangeKind
import com.passly.application.menu.MenuChangedEvent
import com.passly.domain.user.Role
import com.passly.domain.venue.VenueId
import com.passly.web.api.createMember
import com.passly.web.api.createVenue
import com.passly.web.api.mockMvcAs
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.web.context.WebApplicationContext
import java.time.Instant
import java.util.*
import kotlin.test.*

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class MenuEventStreamTest {

    @Autowired
    lateinit var context: WebApplicationContext

    @Autowired
    lateinit var jdbc: JdbcClient

    @Autowired
    lateinit var streams: MenuEventStreams

    @Autowired
    lateinit var categories: CategoryService

    lateinit var waiter: MockMvc
    lateinit var venueUuid: UUID
    lateinit var stationUuid: UUID
    private val venueId get() = VenueId(venueUuid)

    @BeforeEach
    fun setUp() {
        val venue = jdbc.createVenue()
        venueUuid = venue.venueId
        stationUuid = jdbc.createStation(venue)
        waiter = context.mockMvcAs(jdbc.createMember(venue, Role.WAITER))
    }

    private fun openStream(): MockHttpServletResponse =
        waiter.get("/api/v1/venues/${venueId.value}/menu/events") { accept(MediaType.TEXT_EVENT_STREAM) }
            .andExpect { request { asyncStarted() } }
            .andReturn()
            .response

    private fun event(version: Long, change: MenuChangeKind = MenuChangeKind.STOP_LIST) = MenuChangedEvent(
        eventId = UUID.randomUUID().toString(),
        type = MenuChangedEvent.TYPE,
        venueId = venueId.value.toString(),
        menuVersion = version,
        change = change,
        occurredAt = Instant.now().toString(),
    )

    @Test
    fun `stream starts with the current version`() {
        val stream = openStream()

        assertTrue(stream.contentAsString.contains("event:menu"))
        assertTrue(stream.contentAsString.contains("\"menuVersion\":0"))
    }

    @Test
    fun `each new version is sent once and older ones are dropped`() {
        val stream = openStream()

        streams.menuChanged(event(5))
        streams.menuChanged(event(5))
        streams.menuChanged(event(4))

        val body = stream.contentAsString
        assertEquals(1, Regex("\"menuVersion\":5").findAll(body).count())
        assertTrue(body.contains("\"change\":\"STOP_LIST\""))
        assertFalse(body.contains("\"menuVersion\":4"))
    }

    @Test
    fun `without kafka the relay delivers changes in process`() {
        openStream()

        categories.create(CreateCategoryCommand(venueId, "Wine", stationId = StationId(stationUuid)))

        val deadline = Instant.now().plusSeconds(10)
        while (streams.knownVersion(venueId) != 1L) {
            if (Instant.now().isAfter(deadline)) fail("The stream did not learn about version 1")
            Thread.sleep(100)
        }
    }
}
