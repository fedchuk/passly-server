package com.passly.web.api.order

import com.passly.TestcontainersConfiguration
import com.passly.application.floor.CreateAreaCommand
import com.passly.application.floor.CreateTableCommand
import com.passly.application.floor.FloorPlanService
import com.passly.application.menu.*
import com.passly.domain.common.Money
import com.passly.domain.menu.*
import com.passly.domain.venue.VenueId
import com.passly.web.api.*
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.web.context.WebApplicationContext
import java.util.*
import kotlin.test.Test

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class OrderControllerTest {

    @Autowired
    lateinit var context: WebApplicationContext

    @Autowired
    lateinit var jdbc: JdbcClient

    @Autowired
    lateinit var floors: FloorPlanService

    @Autowired
    lateinit var categories: CategoryService

    @Autowired
    lateinit var items: MenuItemService

    @Autowired
    lateinit var modifierGroups: ModifierGroupService

    lateinit var venue: TestVenue
    lateinit var waiter: MockMvc
    lateinit var waiterUuid: UUID
    lateinit var tableUuid: UUID
    lateinit var stationUuid: UUID
    lateinit var mainsUuid: UUID

    private val venueId get() = VenueId(venue.venueId)
    private val base get() = "/api/v1/venues/${venue.venueId}/orders"

    @BeforeEach
    fun setUp() {
        venue = jdbc.createVenue()
        waiterUuid = jdbc.createMember(venue, StaffRole.WAITER)
        waiter = context.mockMvcAs(waiterUuid)
        stationUuid = jdbc.createStation(venue)
        val hall = floors.createArea(CreateAreaCommand(venueId, "Hall"))
        tableUuid = floors.createTable(CreateTableCommand(venueId, hall.id, "7", 4)).id.value
        mainsUuid = categories.create(CreateCategoryCommand(venueId, "Mains", stationId = StationId(stationUuid))).id.value
    }

    private fun item(name: String, price: String): MenuItem = items.create(
        CreateMenuItemCommand(
            venueId,
            CategoryId(mainsUuid),
            MenuItemDetails(name, null, Money.of(price))
        ),
    )

    private fun openTable(json: String) = waiter.post(base) {
        contentType = MediaType.APPLICATION_JSON
        content = json
    }

    private fun openedOrderId(json: String): String =
        openTable(json).andExpect { status { isCreated() } }
            .andReturn().response.getHeader("Location")!!.substringAfterLast('/')

    @Test
    fun `waiter opens a table with the first round and gets the priced order`() {
        val steak = item("Ribeye", "26.00")
        val doneness = modifierGroups.create(
            CreateModifierGroupCommand(
                venueId, "Doneness", 1, 1,
                listOf(ModifierDraft("Rare", Money.of("0")), ModifierDraft("Well done", Money.of("0.50"))),
            ),
        )
        items.attachModifierGroups(AttachModifierGroupsCommand(venueId, steak.id, listOf(doneness.id)))
        val wellDone = doneness.modifiers[1].id.value

        openTable(
            """{"tableId": "$tableUuid", "guests": 2, "lines": [
                {"menuItemId": "${steak.id}", "quantity": 2, "modifierIds": ["$wellDone"], "comment": "sauce on the side"}
            ]}""",
        ).andExpect {
            status { isCreated() }
            header { string("Location", org.hamcrest.Matchers.startsWith("/api/v1/venues/${venue.venueId}/orders/")) }
            jsonPath("$.tableId") { value(tableUuid.toString()) }
            jsonPath("$.status") { value("OPEN") }
            jsonPath("$.openedBy") { value(waiterUuid.toString()) }
            jsonPath("$.rounds") { value(1) }
            jsonPath("$.total") { value("53.00") }
            jsonPath("$.lines[0].name") { value("Ribeye") }
            jsonPath("$.lines[0].unitPrice") { value("26.50") }
            jsonPath("$.lines[0].stationId") { value(stationUuid.toString()) }
            jsonPath("$.lines[0].modifiers[0].group") { value("Doneness") }
            jsonPath("$.lines[0].modifiers[0].name") { value("Well done") }
            jsonPath("$.lines[0].comment") { value("sauce on the side") }
        }
    }

    @Test
    fun `next round is added to the open order`() {
        val coffee = item("Espresso", "1.80")
        val order = openedOrderId("""{"tableId": "$tableUuid", "lines": [{"menuItemId": "${coffee.id}", "quantity": 1}]}""")

        waiter.post("$base/$order/rounds") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"lines": [{"menuItemId": "${coffee.id}", "quantity": 2}]}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.rounds") { value(2) }
            jsonPath("$.lines[1].round") { value(2) }
            jsonPath("$.total") { value("5.40") }
        }
    }

    @Test
    fun `a stop-listed item rejects the round and names it`() {
        val salmon = item("Salmon", "19.00")
        items.changeAvailability(venueId, salmon.id, available = false)
        val order = openedOrderId("""{"tableId": "$tableUuid"}""")

        waiter.post("$base/$order/rounds") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"lines": [{"menuItemId": "${salmon.id}", "quantity": 1}]}"""
        }.andExpect {
            status { isConflict() }
            jsonPath("$.unavailableItemIds[0]") { value(salmon.id.value.toString()) }
        }

        waiter.get("$base/$order").andExpect { jsonPath("$.lines.length()") { value(0) } }
    }

    @Test
    fun `a table with an open order cannot be opened twice`() {
        openedOrderId("""{"tableId": "$tableUuid"}""")

        openTable("""{"tableId": "$tableUuid"}""").andExpect {
            status { isConflict() }
            jsonPath("$.detail") { value("Table 7 already has an open order") }
        }
    }

    @Test
    fun `open orders are listed and an order can be read`() {
        val order = openedOrderId("""{"tableId": "$tableUuid", "guests": 3}""")

        waiter.get(base).andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].id") { value(order) }
        }
        waiter.get("$base/$order").andExpect {
            status { isOk() }
            jsonPath("$.guests") { value(3) }
        }
        waiter.get("$base/${UUID.randomUUID()}").andExpect { status { isNotFound() } }
    }

    @Test
    fun `invalid quantity and an empty round are rejected`() {
        val coffee = item("Espresso", "1.80")

        openTable("""{"tableId": "$tableUuid", "lines": [{"menuItemId": "${coffee.id}", "quantity": 0}]}""").andExpect {
            status { isBadRequest() }
        }
        val order = openedOrderId("""{"tableId": "$tableUuid"}""")
        waiter.post("$base/$order/rounds") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"lines": []}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.lines") { exists() }
        }
    }

    @Test
    fun `kitchen cannot take orders`() {
        val kitchen = context.mockMvcAs(jdbc.createMember(venue, StaffRole.KITCHEN))

        kitchen.get(base).andExpect { status { isForbidden() } }
        kitchen.post(base) {
            contentType = MediaType.APPLICATION_JSON
            content = """{"tableId": "$tableUuid"}"""
        }.andExpect { status { isForbidden() } }
    }
}
