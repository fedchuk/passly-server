package com.passly.application.access

import com.passly.application.menu.FakeVenueRepository
import com.passly.application.user.FakeUserRepository
import com.passly.domain.user.Email
import com.passly.domain.user.Permission
import com.passly.domain.user.Role
import com.passly.domain.user.User
import com.passly.domain.user.UserId
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.Venue
import com.passly.domain.venue.VenueId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccessServiceTest {

    private val tenant = TenantId.new()
    private val centro = Venue(VenueId.new(), tenant, "Centro")
    private val competitor = Venue(VenueId.new(), TenantId.new(), "Competitor")

    private val users = FakeUserRepository()
    private val playa = Venue(VenueId.new(), tenant, "Playa")
    private val service = AccessService(users, FakeVenueRepository(centro, playa, competitor))

    private fun member(role: Role, venue: Venue = centro) =
        User.create(Email.of("${role.name.lowercase()}@bistro.es"), role.name, "hash")
            .apply { if (role == Role.OWNER) becomeOwnerOf(venue.tenantId) else joinVenue(venue, role) }
            .also(users::save)

    @Test
    fun `waiter can only look at the menu`() {
        val waiter = member(Role.WAITER)

        assertTrue(service.isAllowed(waiter.id, centro.id, Permission.VIEW_MENU))
        assertFalse(service.isAllowed(waiter.id, centro.id, Permission.EDIT_MENU))
        assertFalse(service.isAllowed(waiter.id, centro.id, Permission.MANAGE_STOP_LIST))
    }

    @Test
    fun `kitchen can put dishes on the stop list but not edit them`() {
        val kitchen = member(Role.KITCHEN)

        assertTrue(service.isAllowed(kitchen.id, centro.id, Permission.MANAGE_STOP_LIST))
        assertFalse(service.isAllowed(kitchen.id, centro.id, Permission.EDIT_MENU))
    }

    @Test
    fun `manager and owner can do everything in their venue`() {
        val manager = member(Role.MANAGER)
        val owner = member(Role.OWNER)

        Permission.entries.forEach {
            assertTrue(service.isAllowed(manager.id, centro.id, it))
            assertTrue(service.isAllowed(owner.id, centro.id, it))
        }
    }

    @Test
    fun `nobody gets into another business`() {
        val owner = member(Role.OWNER)

        assertFalse(service.isAllowed(owner.id, competitor.id, Permission.VIEW_MENU))
    }

    @Test
    fun `unknown user or venue is denied`() {
        val manager = member(Role.MANAGER)

        assertFalse(service.isAllowed(UserId.new(), centro.id, Permission.VIEW_MENU))
        assertFalse(service.isAllowed(manager.id, VenueId.new(), Permission.VIEW_MENU))
    }

    @Test
    fun `owner sees every venue of the business sorted by name`() {
        val owner = member(Role.OWNER)

        val venues = service.venuesOf(owner.id)

        assertEquals(listOf("Centro", "Playa"), venues.map { it.name })
        assertTrue(venues.all { it.role == Role.OWNER })
    }

    @Test
    fun `staff member sees only the venues they work at`() {
        val waiter = member(Role.WAITER)

        assertEquals(listOf(AccessibleVenue(centro.id, "Centro", Role.WAITER)), service.venuesOf(waiter.id))
    }

    @Test
    fun `unknown user sees nothing`() {
        assertTrue(service.venuesOf(UserId.new()).isEmpty())
    }
}
