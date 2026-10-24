package com.passly.application.outbox

import com.passly.web.api.createStation
import com.passly.domain.menu.StationId
import com.passly.TestcontainersConfiguration
import com.passly.application.menu.CategoryService
import com.passly.application.menu.CreateCategoryCommand
import com.passly.application.menu.MenuChangeKind
import com.passly.application.menu.MenuChangeRecorder
import com.passly.domain.venue.VenueId
import com.passly.web.api.TestVenue
import com.passly.web.api.createVenue
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class OutboxMenuChangeRecorderTest {

    @Autowired
    lateinit var jdbc: JdbcClient

    @Autowired
    lateinit var categories: CategoryService

    @Autowired
    lateinit var recorder: MenuChangeRecorder

    @Autowired
    lateinit var transactions: TransactionTemplate

    lateinit var venue: TestVenue

    private val venueId get() = VenueId(venue.venueId)

    @BeforeEach
    fun setUp() {
        venue = jdbc.createVenue()
    }

    private fun menuVersion(): Long =
        jdbc.sql("select menu_version from venue where id = ?")
            .param(venue.venueId)
            .query { rs, _ -> rs.getLong(1) }
            .single()

    private fun recordedVersions(): List<Long> =
        jdbc.sql(
            """
            select (payload ->> 'menuVersion')::bigint
            from outbox_event
            where aggregate_id = ? and message_key = ?
            order by position
            """,
        )
            .params(venue.venueId, venue.venueId.toString())
            .query { rs, _ -> rs.getLong(1) }
            .list()

    @Test
    fun `each menu change bumps the version and writes an event`() {
        val station = StationId(jdbc.createStation(venue))
        categories.create(CreateCategoryCommand(venueId, "Wine", stationId = station))
        categories.create(CreateCategoryCommand(venueId, "Beer", stationId = station))

        assertEquals(2, menuVersion())
        assertEquals(listOf(1L, 2L), recordedVersions())
    }

    @Test
    fun `event disappears together with a rolled back change`() {
        assertFailsWith<IllegalStateException> {
            transactions.executeWithoutResult {
                recorder.recordChange(venueId, MenuChangeKind.ITEMS)
                throw IllegalStateException("Saving the menu failed")
            }
        }

        assertEquals(0, menuVersion())
        assertEquals(emptyList<Long>(), recordedVersions())
    }
}
