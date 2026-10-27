package com.passly.web.api.menu

import com.passly.TestcontainersConfiguration
import com.passly.web.api.StaffRole
import com.passly.web.api.createMember
import com.passly.web.api.createStation
import com.passly.web.api.createVenue
import com.passly.web.api.mockMvcAs
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.get
import org.springframework.web.context.WebApplicationContext
import kotlin.test.Test

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class StationControllerTest {

    @Autowired
    lateinit var context: WebApplicationContext

    @Autowired
    lateinit var jdbc: JdbcClient

    @Test
    fun `waiter sees the stations of the venue`() {
        val venue = jdbc.createVenue()
        val kitchen = jdbc.createStation(venue, "Kitchen")
        val waiter = context.mockMvcAs(jdbc.createMember(venue, StaffRole.WAITER))

        waiter.get("/api/v1/venues/${venue.venueId}/stations").andExpect {
            status { isOk() }
            jsonPath("$[0].id") { value(kitchen.toString()) }
            jsonPath("$[0].name") { value("Kitchen") }
        }
    }

    @Test
    fun `staff of another venue cannot see the stations`() {
        val venue = jdbc.createVenue()
        val stranger = context.mockMvcAs(jdbc.createMember(jdbc.createVenue(), StaffRole.MANAGER))

        stranger.get("/api/v1/venues/${venue.venueId}/stations").andExpect { status { isForbidden() } }
    }
}
