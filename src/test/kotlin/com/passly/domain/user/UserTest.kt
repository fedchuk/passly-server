package com.passly.domain.user

import com.passly.domain.common.ConflictException
import com.passly.domain.common.NotFoundException
import com.passly.domain.common.ValidationException
import com.passly.domain.role.VenueRoleId
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.Venue
import com.passly.domain.venue.VenueId
import kotlin.test.*

class UserTest {

    private val tenant = TenantId.new()
    private val centro = Venue(VenueId.new(), tenant, "Centro")
    private val playa = Venue(VenueId.new(), tenant, "Playa")
    private val waiter = VenueRoleId.new()
    private val manager = VenueRoleId.new()

    private fun user() = User.create(Email.of("anna@bistro.es"), "Anna", "hash")

    @Test
    fun `staff member has a role only in the venue they joined`() {
        val anna = user().apply { joinVenue(centro, waiter) }

        assertEquals(waiter, anna.roleIdIn(centro.id))
        assertNull(anna.roleIdIn(playa.id))
        assertFalse(anna.owns(tenant))
    }

    @Test
    fun `owner owns the whole business and has no venue role`() {
        val anna = user().apply { becomeOwnerOf(tenant) }

        assertTrue(anna.owns(tenant))
        assertFalse(anna.owns(TenantId.new()))
        assertNull(anna.roleIdIn(centro.id))
        assertFailsWith<ConflictException> { anna.becomeOwnerOf(tenant) }
    }

    @Test
    fun `one person can have different roles in different venues`() {
        val anna = user().apply {
            joinVenue(centro, manager)
            joinVenue(playa, waiter)
        }

        assertEquals(manager, anna.roleIdIn(centro.id))
        assertEquals(waiter, anna.roleIdIn(playa.id))
    }

    @Test
    fun `joining the same venue twice is a conflict`() {
        val anna = user().apply { joinVenue(centro, waiter) }

        assertFailsWith<ConflictException> { anna.joinVenue(centro, manager) }
    }

    @Test
    fun `a venue membership always has a role and an owner membership never has one`() {
        assertFailsWith<IllegalArgumentException> { Membership(tenant, centro.id, null) }
        assertFailsWith<IllegalArgumentException> { Membership(tenant, null, waiter) }
        assertTrue(Membership(tenant, null, null).isOwner)
    }

    @Test
    fun `changing a role keeps other memberships`() {
        val anna = user().apply {
            joinVenue(centro, waiter)
            joinVenue(playa, waiter)
        }

        anna.changeRole(centro, manager)

        assertEquals(manager, anna.roleIdIn(centro.id))
        assertEquals(waiter, anna.roleIdIn(playa.id))
    }

    @Test
    fun `leaving a venue removes access to it`() {
        val anna = user().apply { joinVenue(centro, waiter) }

        anna.leaveVenue(centro.id)

        assertNull(anna.roleIdIn(centro.id))
        assertFailsWith<NotFoundException> { anna.leaveVenue(centro.id) }
        assertFailsWith<NotFoundException> { anna.changeRole(centro, manager) }
    }

    @Test
    fun `platform admin never works at venues`() {
        val admin = User.createPlatformAdmin(Email.of("max@passly.app"), "Max", "hash")

        assertFailsWith<ValidationException> { admin.joinVenue(centro, manager) }
        assertFailsWith<ValidationException> { admin.becomeOwnerOf(tenant) }
        assertNull(admin.roleIdIn(centro.id))
    }

    @Test
    fun `rename trims the name and rejects a blank one`() {
        val user = User.create(Email.of("max@test.passly"), "Max", "hash")

        user.rename("  Maksym ")

        assertEquals("Maksym", user.displayName)
        assertFailsWith<ValidationException> { user.rename("   ") }
        assertFailsWith<ValidationException> { user.rename("x".repeat(User.MAX_NAME_LENGTH + 1)) }
        assertEquals("Maksym", user.displayName)
    }
}
