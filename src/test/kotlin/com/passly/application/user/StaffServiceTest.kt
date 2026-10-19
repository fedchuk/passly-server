package com.passly.application.user

import com.passly.application.menu.FakeVenueRepository
import com.passly.domain.common.ConflictException
import com.passly.domain.common.NotFoundException
import com.passly.domain.user.Email
import com.passly.domain.user.RawPassword
import com.passly.domain.user.Role
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.Venue
import com.passly.domain.venue.VenueId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StaffServiceTest {

    private val tenant = TenantId.new()
    private val centro = Venue(VenueId.new(), tenant, "Centro")
    private val playa = Venue(VenueId.new(), tenant, "Playa")

    private val users = FakeUserRepository()
    private val service = StaffService(FakeVenueRepository(centro, playa), users, FakePasswordHasher())

    private fun hire(venue: Venue, email: String, name: String, role: Role = Role.WAITER) =
        service.hire(HireStaffCommand(venue.id, Email.of(email), name, RawPassword.of("password-1"), role))

    @Test
    fun `hired staff member is stored with a hashed password`() {
        val anna = hire(centro, "anna@bistro.es", "Anna")

        val stored = users.stored.getValue(anna.id)
        assertEquals("hashed:password-1", stored.passwordHash)
        assertEquals(Role.WAITER, stored.roleIn(centro))
    }

    @Test
    fun `email can be used only once, whatever the case`() {
        hire(centro, "anna@bistro.es", "Anna")

        assertFailsWith<ConflictException> { hire(playa, "ANNA@bistro.es", "Anna again") }
    }

    @Test
    fun `list shows only the staff of the venue, sorted by name`() {
        hire(centro, "pablo@bistro.es", "pablo", Role.KITCHEN)
        hire(centro, "anna@bistro.es", "Anna")
        hire(playa, "lucia@bistro.es", "Lucia")

        val staff = service.list(centro.id)

        assertEquals(listOf("Anna", "pablo"), staff.map { it.displayName })
        assertEquals(listOf(Role.WAITER, Role.KITCHEN), staff.map { it.role })
    }

    @Test
    fun `change role is saved`() {
        val anna = hire(centro, "anna@bistro.es", "Anna")

        service.changeRole(ChangeStaffRoleCommand(centro.id, anna.id, Role.MANAGER))

        assertEquals(Role.MANAGER, users.stored.getValue(anna.id).roleIn(centro))
    }

    @Test
    fun `staff of another venue cannot be dismissed through this venue`() {
        val lucia = hire(playa, "lucia@bistro.es", "Lucia")

        assertFailsWith<NotFoundException> { service.dismiss(centro.id, lucia.id) }
        assertEquals(Role.WAITER, users.stored.getValue(lucia.id).roleIn(playa))
    }

    @Test
    fun `dismissed staff member keeps the account but loses the venue`() {
        val anna = hire(centro, "anna@bistro.es", "Anna")

        service.dismiss(centro.id, anna.id)

        assertTrue(anna.id in users.stored)
        assertTrue(service.list(centro.id).isEmpty())
    }

    @Test
    fun `unknown venue is not found`() {
        assertFailsWith<NotFoundException> { service.list(VenueId.new()) }
    }
}
