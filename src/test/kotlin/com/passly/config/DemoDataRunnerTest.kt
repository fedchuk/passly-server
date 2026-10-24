package com.passly.config

import com.passly.TestcontainersConfiguration
import com.passly.application.access.AccessService
import com.passly.application.floor.FloorPlanService
import com.passly.application.menu.WaiterMenuService
import com.passly.domain.user.Email
import com.passly.domain.user.UserRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

@SpringBootTest
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration::class)
class DemoDataRunnerTest {

    @Autowired
    lateinit var runner: DemoDataRunner

    @Autowired
    lateinit var users: UserRepository

    @Autowired
    lateinit var access: AccessService

    @Autowired
    lateinit var menus: WaiterMenuService

    @Autowired
    lateinit var floors: FloorPlanService

    private fun demoVenue() =
        access.venuesOf(users.findByEmail(DemoDataRunner.OWNER_EMAIL)!!.id).single().venueId

    @Test
    fun `demo profile fills the venue on startup`() {
        val venueId = demoVenue()

        val menu = menus.menu(venueId)
        assertEquals(listOf("Food", "Drinks"), menu.categories.map { it.name })
        assertFalse(menu.categories[0].children.flatMap { it.children }.flatMap { it.items }.single { it.name == "Sea bass" }.available)

        val floor = floors.get(venueId)
        assertEquals(listOf("Main hall", "Terrace", "Bar counter"), floor.areas.map { it.name })
        assertEquals(24, floor.tables.size)
        assertNotNull(users.findByEmail(Email.of("pablo@${DemoDataRunner.DOMAIN}")))
    }

    @Test
    fun `second run changes nothing`() {
        val venueId = demoVenue()
        val version = menus.currentVersion(venueId)

        runner.run(DefaultApplicationArguments())

        assertEquals(version, menus.currentVersion(venueId))
        assertEquals(24, floors.get(venueId).tables.size)
    }
}
