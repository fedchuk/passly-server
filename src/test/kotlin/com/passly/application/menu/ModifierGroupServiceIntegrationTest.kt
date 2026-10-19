package com.passly.application.menu

import com.passly.TestcontainersConfiguration
import com.passly.domain.common.Money
import com.passly.domain.common.NotFoundException
import com.passly.domain.menu.ModifierDraft
import com.passly.domain.menu.ModifierId
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

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class ModifierGroupServiceIntegrationTest {

    @Autowired
    lateinit var service: ModifierGroupService

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

    private fun option(name: String, delta: String = "0", id: ModifierId? = null) =
        ModifierDraft(name, Money.of(delta), id)

    @Test
    fun `group survives a round trip with option order and negative prices`() {
        val extras = service.create(
            CreateModifierGroupCommand(venueId, "Extras", 0, 2, listOf(option("Cheese", "1.00"), option("No onion", "-0.20"))),
        )

        val stored = service.get(venueId, extras.id)

        assertEquals(listOf("Cheese", "No onion"), stored.modifiers.map { it.name })
        assertEquals(Money.of("-0.20"), stored.modifiers[1].priceDelta)
        assertEquals(0L, stored.version)
    }

    @Test
    fun `swapping options is persisted and bumps the group version`() {
        val size = service.create(CreateModifierGroupCommand(venueId, "Size", 1, 1, listOf(option("Small"), option("Large"))))
        val (small, large) = size.modifiers

        service.update(
            UpdateModifierGroupCommand(
                venueId, size.id, "Size", 1, 1,
                listOf(option("Large", id = large.id), option("Small", id = small.id)),
                expectedVersion = 0,
            ),
        )

        val stored = service.get(venueId, size.id)
        assertEquals(listOf(large.id, small.id), stored.modifiers.map { it.id })
        assertEquals(1L, stored.version)
    }

    @Test
    fun `deleted group disappears together with its options`() {
        val size = service.create(CreateModifierGroupCommand(venueId, "Size", 1, 1, listOf(option("Small"), option("Large"))))

        service.delete(venueId, size.id)

        assertFailsWith<NotFoundException> { service.get(venueId, size.id) }
        val left = jdbc.sql("select count(*) from modifier where group_id = ?")
            .param(size.id.value)
            .query(Long::class.javaObjectType)
            .single()
        assertEquals(0L, left)
    }
}
