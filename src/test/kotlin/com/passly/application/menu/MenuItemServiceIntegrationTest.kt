package com.passly.application.menu

import com.passly.TestcontainersConfiguration
import com.passly.domain.common.ConflictException
import com.passly.domain.common.Money
import com.passly.domain.common.TaxRate
import com.passly.domain.menu.CategoryId
import com.passly.domain.menu.MenuItemDetails
import com.passly.domain.venue.VenueId
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class MenuItemServiceIntegrationTest {

    @Autowired
    lateinit var service: MenuItemService

    @Autowired
    lateinit var categoryService: CategoryService

    @Autowired
    lateinit var jdbc: JdbcClient

    private val venueId = VenueId(UUID.randomUUID())

    @BeforeEach
    fun createVenue() {
        val tenantId = UUID.randomUUID()
        jdbc.sql("insert into tenant (id, name) values (?, ?)").params(tenantId, "Test tenant").update()
        jdbc.sql("insert into venue (id, tenant_id, name) values (?, ?, ?)")
            .params(venueId.value, tenantId, "Test venue")
            .update()
    }

    @Test
    fun `item survives a round trip through the database`() {
        val wine = category("Wine")
        val rioja = service.create(CreateMenuItemCommand(venueId, wine, details("Rioja", "4.5")))

        service.update(UpdateMenuItemCommand(venueId, rioja.id, details("Rioja Crianza", "4.80"), expectedVersion = 0))

        val stored = service.get(venueId, rioja.id)
        assertEquals("Rioja Crianza", stored.name)
        assertEquals(Money.of("4.80"), stored.price)
        assertEquals(TaxRate.of("10"), stored.taxRate)
        assertEquals(1L, stored.version)
    }

    @Test
    fun `move, reorder and archive are persisted`() {
        val steaks = category("Steaks")
        val fish = category("Fish")
        val salmon = create(steaks, "Salmon")
        val ribeye = create(steaks, "Ribeye")
        val picanha = create(steaks, "Picanha")

        service.move(MoveMenuItemCommand(venueId, salmon.id, fish))
        service.reorder(ReorderMenuItemsCommand(venueId, steaks, listOf(picanha.id, ribeye.id)))
        service.archive(venueId, ribeye.id)

        assertEquals(listOf("Picanha"), service.listInCategory(venueId, steaks).map { it.name })
        assertEquals(listOf("Salmon"), service.listInCategory(venueId, fish).map { it.name })
    }

    @Test
    fun `category can be deleted only after its items are archived`() {
        val steaks = category("Steaks")
        val ribeye = create(steaks, "Ribeye")

        assertFailsWith<ConflictException> { categoryService.delete(venueId, steaks) }

        service.archive(venueId, ribeye.id)
        categoryService.delete(venueId, steaks)

        assertTrue(steaks !in categoryService.getTree(venueId))
    }

    private fun category(name: String): CategoryId =
        categoryService.create(CreateCategoryCommand(venueId, name)).id

    private fun details(name: String, price: String = "10.00") =
        MenuItemDetails(name, null, Money.of(price), TaxRate.of("10"))

    private fun create(category: CategoryId, name: String) =
        service.create(CreateMenuItemCommand(venueId, category, details(name)))
}
