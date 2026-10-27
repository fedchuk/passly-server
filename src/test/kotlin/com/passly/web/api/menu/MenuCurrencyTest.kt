package com.passly.web.api.menu

import com.passly.TestcontainersConfiguration
import com.passly.application.venue.UpdateVenueSettingsCommand
import com.passly.application.venue.VenueSettingsService
import com.passly.domain.venue.CountryCode
import com.passly.domain.venue.CurrencyCode
import com.passly.domain.venue.VenueId
import com.passly.web.api.StaffRole
import com.passly.web.api.createMember
import com.passly.web.api.createVenue
import com.passly.web.api.mockMvcAs
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.web.context.WebApplicationContext
import java.util.*
import kotlin.test.Test
import kotlin.test.assertNotEquals

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class MenuCurrencyTest {

    @Autowired
    lateinit var context: WebApplicationContext

    @Autowired
    lateinit var jdbc: JdbcClient

    @Autowired
    lateinit var settings: VenueSettingsService

    lateinit var waiter: MockMvc
    lateinit var venueUuid: UUID

    private val base get() = "/api/v1/venues/$venueUuid/menu"

    @BeforeEach
    fun setUp() {
        val venue = jdbc.createVenue()
        venueUuid = venue.venueId
        waiter = context.mockMvcAs(jdbc.createMember(venue, StaffRole.WAITER))
    }

    @Test
    fun `menu tells the currency of its prices`() {
        waiter.get(base).andExpect {
            status { isOk() }
            jsonPath("$.currency") { value("EUR") }
        }
    }

    @Test
    fun `new main currency reaches the waiter as a new menu version`() {
        val etag = waiter.get(base).andReturn().response.getHeader("ETag")

        settings.update(UpdateVenueSettingsCommand(VenueId(venueUuid), CountryCode.of("CH"), CurrencyCode.of("CHF")))

        val response = waiter.get(base).andExpect {
            status { isOk() }
            jsonPath("$.currency") { value("CHF") }
        }.andReturn().response
        assertNotEquals(etag, response.getHeader("ETag"))
    }
}
