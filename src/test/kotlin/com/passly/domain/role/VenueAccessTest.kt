package com.passly.domain.role

import com.passly.domain.user.Email
import com.passly.domain.user.Permission
import com.passly.domain.user.User
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.Venue
import com.passly.domain.venue.VenueId
import kotlin.test.*

class VenueAccessTest {

    private val tenant = TenantId.new()
    private val centro = Venue(VenueId.new(), tenant, "Centro")
    private val playa = Venue(VenueId.new(), tenant, "Playa")
    private val roles = RoleList(centro.id, emptyList())
    private val waiter = roles.add(tenant, "Waiter", setOf(Permission.TAKE_ORDERS, Permission.CLOSE_ORDERS))
    private val host = roles.add(tenant, "Host", setOf(Permission.TAKE_ORDERS))

    private fun user() = User.create(Email.of("anna@bistro.es"), "Anna", "hash")

    @Test
    fun `staff member gets the permissions of their role`() {
        val anna = user().apply { joinVenue(centro, waiter.id) }

        val access = assertNotNull(VenueAccess.of(anna, centro, roles))

        assertEquals("Waiter", access.roleName)
        assertTrue(access.allows(Permission.CLOSE_ORDERS))
        assertFalse(access.allows(Permission.EDIT_MENU))
        assertNull(VenueAccess.of(anna, playa, RoleList(playa.id, emptyList())))
    }

    @Test
    fun `owner can do everything, managing roles included`() {
        val owner = user().apply { becomeOwnerOf(tenant) }

        val access = assertNotNull(VenueAccess.of(owner, centro, roles))

        assertTrue(access.owner)
        assertTrue(Permission.entries.all { access.allows(it) })
    }

    @Test
    fun `disabled user has no access`() {
        val owner = user().apply {
            becomeOwnerOf(tenant)
            disable()
        }

        assertNull(VenueAccess.of(owner, centro, roles))
    }

    @Test
    fun `a role can be given only by someone whose role covers it`() {
        val hostAccess = assertNotNull(VenueAccess.of(user().apply { joinVenue(centro, host.id) }, centro, roles))
        val waiterAccess = assertNotNull(VenueAccess.of(user().apply { joinVenue(centro, waiter.id) }, centro, roles))

        assertTrue(waiterAccess.covers(host))
        assertFalse(hostAccess.covers(waiter))
        assertTrue(VenueAccess.OWNER.covers(waiter))
    }
}
