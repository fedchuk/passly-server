package com.passly.application.access

import com.passly.application.menu.FakeVenueRepository
import com.passly.application.role.FakeRoleRepository
import com.passly.application.user.FakeUserRepository
import com.passly.domain.role.RolePreset
import com.passly.domain.role.VenueAccess
import com.passly.domain.user.Email
import com.passly.domain.user.Permission
import com.passly.domain.user.User
import com.passly.domain.user.UserId
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.Venue
import com.passly.domain.venue.VenueId
import kotlin.test.*

class AccessServiceTest {

    private val tenant = TenantId.new()
    private val centro = Venue(VenueId.new(), tenant, "Centro")
    private val playa = Venue(VenueId.new(), tenant, "Playa")
    private val competitor = Venue(VenueId.new(), TenantId.new(), "Competitor")

    private val users = FakeUserRepository()
    private val roles = FakeRoleRepository()
    private val presets = roles.presetsOf(centro)
    private val service = AccessService(users, FakeVenueRepository(centro, playa, competitor), roles)

    private fun member(preset: RolePreset) =
        User.create(Email.of("${preset.name.lowercase()}@bistro.es"), preset.roleName, "hash")
            .apply { joinVenue(centro, presets.getValue(preset)) }
            .also(users::save)

    private fun owner() =
        User.create(Email.of("owner@bistro.es"), "Owner", "hash")
            .apply { becomeOwnerOf(tenant) }
            .also(users::save)

    @Test
    fun `waiter sees the menu and takes orders but does not edit the menu`() {
        val waiter = member(RolePreset.WAITER)

        assertTrue(service.isAllowed(waiter.id, centro.id, Permission.VIEW_MENU))
        assertTrue(service.isAllowed(waiter.id, centro.id, Permission.TAKE_ORDERS))
        assertFalse(service.isAllowed(waiter.id, centro.id, Permission.EDIT_MENU))
        assertFalse(service.isAllowed(waiter.id, centro.id, Permission.MANAGE_STOP_LIST))
    }

    @Test
    fun `new permission of a role works at once`() {
        val waiter = member(RolePreset.WAITER)
        val list = roles.load(centro.id)
        roles.saveAll(listOf(list.grant(presets.getValue(RolePreset.WAITER), setOf(Permission.MANAGE_STOP_LIST))))

        assertTrue(service.isAllowed(waiter.id, centro.id, Permission.MANAGE_STOP_LIST))
        assertFalse(service.isAllowed(waiter.id, centro.id, Permission.TAKE_ORDERS))
    }

    @Test
    fun `owner can do everything in their venues and only owners manage roles`() {
        val owner = owner()
        val manager = member(RolePreset.MANAGER)

        Permission.entries.forEach { assertTrue(service.isAllowed(owner.id, centro.id, it)) }
        assertFalse(service.isAllowed(manager.id, centro.id, Permission.MANAGE_ROLES))
        assertFalse(service.isAllowed(manager.id, centro.id, Permission.MANAGE_STAFF))
    }

    @Test
    fun `nobody gets into another business`() {
        val owner = owner()

        assertFalse(service.isAllowed(owner.id, competitor.id, Permission.VIEW_MENU))
        assertNull(service.accessTo(owner.id, competitor.id))
    }

    @Test
    fun `unknown user or venue is denied`() {
        val manager = member(RolePreset.MANAGER)

        assertFalse(service.isAllowed(UserId.new(), centro.id, Permission.VIEW_MENU))
        assertFalse(service.isAllowed(manager.id, VenueId.new(), Permission.VIEW_MENU))
    }

    @Test
    fun `owner sees every venue of the business sorted by name`() {
        val owner = owner()

        val venues = service.venuesOf(owner.id)

        assertEquals(listOf("Centro", "Playa"), venues.map { it.name })
        assertTrue(venues.all { it.access == VenueAccess.OWNER })
    }

    @Test
    fun `staff member sees only the venues they work at with the name of the role`() {
        val waiter = member(RolePreset.WAITER)

        val venues = service.venuesOf(waiter.id)

        assertEquals(listOf("Centro" to "Waiter"), venues.map { it.name to it.access.roleName })
    }

    @Test
    fun `unknown user sees nothing`() {
        assertTrue(service.venuesOf(UserId.new()).isEmpty())
    }
}
