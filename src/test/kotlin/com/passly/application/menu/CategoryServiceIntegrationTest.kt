package com.passly.application.menu

import com.passly.TestcontainersConfiguration
import com.passly.domain.common.ConflictException
import com.passly.domain.menu.Category
import com.passly.domain.menu.CategoryRepository
import com.passly.domain.menu.StationId
import com.passly.domain.venue.VenueId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFailsWith

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class CategoryServiceIntegrationTest {

    @Autowired
    lateinit var service: CategoryService

    @Autowired
    lateinit var categories: CategoryRepository

    @Autowired
    lateinit var transactions: TransactionTemplate

    @Autowired
    lateinit var jdbc: JdbcClient

    private val tenantId = UUID.randomUUID()
    private val venueId = VenueId(UUID.randomUUID())

    @BeforeEach
    fun createVenue() {
        jdbc.sql("insert into tenant (id, name) values (?, ?)")
            .params(tenantId, "Test tenant")
            .update()
        jdbc.sql("insert into venue (id, tenant_id, name) values (?, ?, ?)")
            .params(venueId.value, tenantId, "Test venue")
            .update()
    }

    @Test
    fun `tree survives a round trip through the database`() {
        val kitchen = createStation("Kitchen")
        val food = create("Food", stationId = kitchen)
        val mains = create("Mains", parent = food)
        create("Starters", parent = food)
        val steaks = create("Steaks", parent = mains)

        val tree = service.getTree(venueId)

        assertEquals(listOf("Mains", "Starters"), tree.children(food.id).map { it.name })
        assertEquals(kitchen, tree.effectiveStation(steaks.id))
        assertEquals(0L, tree[food.id].version)
    }

    @Test
    fun `move and delete are persisted`() {
        val food = create("Food")
        val drinks = create("Drinks")
        val wine = create("Wine", parent = food)
        val desserts = create("Desserts", parent = food)

        service.move(MoveCategoryCommand(venueId, wine.id, newParentId = drinks.id))
        service.delete(venueId, desserts.id)

        val tree = service.getTree(venueId)
        assertEquals(listOf("Wine"), tree.children(drinks.id).map { it.name })
        assertTrue(tree.children(food.id).isEmpty())
        assertTrue(desserts.id !in tree)
    }

    @Test
    fun `category with subcategories stays in the database`() {
        val food = create("Food")
        create("Starters", parent = food)

        assertFailsWith<ConflictException> { service.delete(venueId, food.id) }

        assertTrue(food.id in service.getTree(venueId))
    }

    @Test
    fun `saving a stale category is rejected`() {
        val food = create("Food")
        val stale = service.getTree(venueId)[food.id]
        service.update(UpdateCategoryCommand(venueId, food.id, "Kitchen", stationId = null, active = true))

        assertFailsWith<OptimisticLockingFailureException> {
            transactions.executeWithoutResult {
                stale.rename("Food and more")
                categories.saveAll(listOf(stale))
            }
        }

        assertEquals("Kitchen", service.getTree(venueId)[food.id].name)
    }

    private fun create(name: String, parent: Category? = null, stationId: StationId? = null): Category =
        service.create(CreateCategoryCommand(venueId, name, parent?.id, stationId))

    private fun createStation(name: String): StationId {
        val id = UUID.randomUUID()
        jdbc.sql("insert into station (id, tenant_id, venue_id, name) values (?, ?, ?, ?)")
            .params(id, tenantId, venueId.value, name)
            .update()
        return StationId(id)
    }
}
