package com.passly.application.role

import com.passly.application.menu.FakeVenueRepository
import com.passly.application.user.FakeUserRepository
import com.passly.domain.common.ConflictException
import com.passly.domain.common.NotFoundException
import com.passly.domain.common.ValidationException
import com.passly.domain.role.RolePreset
import com.passly.domain.role.VenueRoleId
import com.passly.domain.user.Email
import com.passly.domain.user.Permission
import com.passly.domain.user.User
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.Venue
import com.passly.domain.venue.VenueId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RoleServiceTest {

    private val tenant = TenantId.new()
    private val centro = Venue(VenueId.new(), tenant, "Centro")
    private val playa = Venue(VenueId.new(), tenant, "Playa")

    private val venues = FakeVenueRepository(centro, playa)
    private val roles = FakeRoleRepository()
    private val users = FakeUserRepository()
    private val presets = roles.presetsOf(centro)
    private val service = RoleService(venues, roles, users)

    private val waiter get() = presets.getValue(RolePreset.WAITER)
    private val kitchen get() = presets.getValue(RolePreset.KITCHEN)

    private fun employ(name: String, roleId: VenueRoleId) =
        User.create(Email.of("$name@bistro.es"), name, "hash").apply { joinVenue(centro, roleId) }.also(users::save)

    @Test
    fun `roles are listed in order with the number of people`() {
        employ("anna", waiter)
        employ("pablo", waiter)

        val list = service.list(centro.id)

        assertEquals(listOf("Manager" to 0, "Waiter" to 2, "Kitchen" to 0), list.map { it.role.name to it.members })
    }

    @Test
    fun `new role can start as a copy of another one`() {
        val bartender = service.create(CreateRoleCommand(centro.id, "Bartender", copyOf = waiter))
        val trainee = service.create(CreateRoleCommand(centro.id, "Trainee"))

        assertEquals(roles.load(centro.id)[waiter].permissions, bartender.permissions)
        assertEquals(Permission.EVERY_ROLE, trainee.permissions)
        assertEquals(2, venues.locks)
    }

    @Test
    fun `matrix saves only the roles that changed`() {
        val versions = roles.load(centro.id).roles.associate { it.id to (it.version ?: 0) }

        val changed = service.grant(
            GrantPermissionsCommand(
                centro.id,
                grants = mapOf(
                    waiter to setOf(Permission.TAKE_ORDERS, Permission.CLOSE_ORDERS, Permission.MANAGE_STOP_LIST),
                    kitchen to setOf(Permission.MANAGE_STOP_LIST, Permission.PREPARE_ORDERS),
                ),
                expectedVersions = versions,
            ),
        )

        assertEquals(listOf(waiter), changed.map { it.id })
        assertTrue(roles.load(centro.id)[waiter].allows(Permission.MANAGE_STOP_LIST))
    }

    @Test
    fun `stale matrix is refused`() {
        val stale = roles.load(centro.id).roles.associate { it.id to (it.version ?: 0) }
        service.rename(RenameRoleCommand(centro.id, waiter, "Server"))

        assertFailsWith<ConflictException> {
            service.grant(GrantPermissionsCommand(centro.id, mapOf(waiter to emptySet()), stale))
        }
        assertTrue(roles.load(centro.id)[waiter].allows(Permission.TAKE_ORDERS))
    }

    @Test
    fun `update renames and replaces the permissions at once`() {
        val version = roles.load(centro.id)[waiter].version

        service.update(UpdateRoleCommand(centro.id, waiter, "Server", setOf(Permission.TAKE_ORDERS), version))

        val stored = roles.load(centro.id)[waiter]
        assertEquals("Server", stored.name)
        assertEquals(setOf(Permission.VIEW_MENU, Permission.TAKE_ORDERS), stored.permissions)
    }

    @Test
    fun `owner only permission cannot be given to a role`() {
        assertFailsWith<ValidationException> {
            service.grant(GrantPermissionsCommand(centro.id, mapOf(waiter to setOf(Permission.MANAGE_ROLES))))
        }
    }

    @Test
    fun `role with people is kept and an empty one is deleted`() {
        employ("anna", waiter)

        val error = assertFailsWith<ConflictException> { service.delete(centro.id, waiter) }
        assertEquals("Role 'Waiter' is given to 1 person. Give them another role first.", error.message)

        service.delete(centro.id, kitchen)
        assertEquals(listOf("Manager", "Waiter"), service.list(centro.id).map { it.role.name })
    }

    @Test
    fun `role of another venue is not found`() {
        assertFailsWith<NotFoundException> { service.delete(playa.id, waiter) }
        assertFailsWith<NotFoundException> { service.list(VenueId.new()) }
    }
}
