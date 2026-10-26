package com.passly.domain.user

import com.passly.domain.common.ConflictException
import com.passly.domain.common.NotFoundException
import com.passly.domain.common.ValidationException
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.Venue
import com.passly.domain.venue.VenueId
import kotlin.test.*

class UserTest {

    private val tenant = TenantId.new()
    private val centro = Venue(VenueId.new(), tenant, "Centro")
    private val playa = Venue(VenueId.new(), tenant, "Playa")
    private val competitor = Venue(VenueId.new(), TenantId.new(), "Competitor")

    private fun user() = User.create(Email.of("anna@bistro.es"), "Anna", "hash")

    @Test
    fun `staff member has a role only in the venue they joined`() {
        val anna = user().apply { joinVenue(centro, Role.WAITER) }

        assertEquals(Role.WAITER, anna.roleIn(centro))
        assertNull(anna.roleIn(playa))
    }

    @Test
    fun `owner is owner in every venue of their business and nowhere else`() {
        val anna = user().apply { becomeOwnerOf(tenant) }

        assertEquals(Role.OWNER, anna.roleIn(centro))
        assertEquals(Role.OWNER, anna.roleIn(playa))
        assertNull(anna.roleIn(competitor))
    }

    @Test
    fun `one person can have different roles in different venues`() {
        val anna = user().apply {
            joinVenue(centro, Role.MANAGER)
            joinVenue(playa, Role.WAITER)
        }

        assertEquals(Role.MANAGER, anna.roleIn(centro))
        assertEquals(Role.WAITER, anna.roleIn(playa))
    }

    @Test
    fun `joining the same venue twice is a conflict`() {
        val anna = user().apply { joinVenue(centro, Role.WAITER) }

        assertFailsWith<ConflictException> { anna.joinVenue(centro, Role.KITCHEN) }
    }

    @Test
    fun `owner role cannot be given for a single venue`() {
        assertFailsWith<ValidationException> { user().joinVenue(centro, Role.OWNER) }
        val anna = user().apply { joinVenue(centro, Role.WAITER) }
        assertFailsWith<ValidationException> { anna.changeRole(centro, Role.OWNER) }
    }

    @Test
    fun `changing a role keeps other memberships`() {
        val anna = user().apply {
            joinVenue(centro, Role.WAITER)
            joinVenue(playa, Role.WAITER)
        }

        anna.changeRole(centro, Role.MANAGER)

        assertEquals(Role.MANAGER, anna.roleIn(centro))
        assertEquals(Role.WAITER, anna.roleIn(playa))
    }

    @Test
    fun `leaving a venue removes access to it`() {
        val anna = user().apply { joinVenue(centro, Role.WAITER) }

        anna.leaveVenue(centro.id)

        assertNull(anna.roleIn(centro))
        assertFailsWith<NotFoundException> { anna.leaveVenue(centro.id) }
    }

    @Test
    fun `disabled user has no role anywhere`() {
        val anna = user().apply {
            becomeOwnerOf(tenant)
            disable()
        }

        assertNull(anna.roleIn(centro))
    }

    @Test
    fun `platform admin never works at venues`() {
        val admin = User.createPlatformAdmin(Email.of("max@passly.app"), "Max", "hash")

        assertFailsWith<ValidationException> { admin.joinVenue(centro, Role.MANAGER) }
        assertFailsWith<ValidationException> { admin.becomeOwnerOf(tenant) }
        assertNull(admin.roleIn(centro))
    }

    @Test
    fun `permissions follow the role in the venue`() {
        val anna = user().apply { joinVenue(centro, Role.KITCHEN) }

        assertTrue(anna.can(Permission.MANAGE_STOP_LIST, centro))
        assertFalse(anna.can(Permission.EDIT_MENU, centro))
        assertFalse(anna.can(Permission.VIEW_MENU, playa))
    }

    @Test
    fun `waiters take orders and the kitchen does not`() {
        val pablo = user().apply { joinVenue(centro, Role.WAITER) }
        val anna = user().apply { joinVenue(centro, Role.KITCHEN) }

        assertTrue(pablo.can(Permission.TAKE_ORDERS, centro))
        assertFalse(anna.can(Permission.TAKE_ORDERS, centro))
    }

    @Test
    fun `kitchen prepares orders and waiters do not`() {
        val pablo = user().apply { joinVenue(centro, Role.WAITER) }
        val anna = user().apply { joinVenue(centro, Role.KITCHEN) }

        assertTrue(anna.can(Permission.PREPARE_ORDERS, centro))
        assertFalse(pablo.can(Permission.PREPARE_ORDERS, centro))
    }

    @Test
    fun `waiters close tables and the kitchen does not`() {
        val pablo = user().apply { joinVenue(centro, Role.WAITER) }
        val anna = user().apply { joinVenue(centro, Role.KITCHEN) }

        assertTrue(pablo.can(Permission.CLOSE_ORDERS, centro))
        assertFalse(anna.can(Permission.CLOSE_ORDERS, centro))
    }

    @Test
    fun `only the owner manages the staff`() {
        val anna = user().apply { joinVenue(centro, Role.MANAGER) }
        val owner = user().apply { becomeOwnerOf(tenant) }

        assertFalse(anna.can(Permission.MANAGE_STAFF, centro))
        assertTrue(owner.can(Permission.MANAGE_STAFF, centro))
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
