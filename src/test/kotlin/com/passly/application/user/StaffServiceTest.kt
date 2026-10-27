package com.passly.application.user

import com.passly.application.menu.FakeVenueRepository
import com.passly.application.role.FakeRoleRepository
import com.passly.domain.common.ConflictException
import com.passly.domain.common.ForbiddenException
import com.passly.domain.common.NotFoundException
import com.passly.domain.role.RolePreset
import com.passly.domain.role.VenueRoleId
import com.passly.domain.user.Email
import com.passly.domain.user.RawPassword
import com.passly.domain.user.User
import com.passly.domain.user.UserId
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
    private val roles = FakeRoleRepository()
    private val centroRoles = roles.presetsOf(centro)
    private val playaRoles = roles.presetsOf(playa)
    private val service = StaffService(FakeVenueRepository(centro, playa), users, roles, FakePasswordHasher())

    private fun role(venue: Venue, preset: RolePreset): VenueRoleId =
        (if (venue == centro) centroRoles else playaRoles).getValue(preset)

    private fun hire(venue: Venue, email: String, name: String, preset: RolePreset = RolePreset.WAITER, by: UserId? = null) =
        service.hire(HireStaffCommand(venue.id, Email.of(email), name, RawPassword.of("password-1"), role(venue, preset), by))

    @Test
    fun `hired staff member is stored with a hashed password and the role`() {
        val anna = hire(centro, "anna@bistro.es", "Anna")

        val stored = users.stored.getValue(anna.userId)
        assertEquals("hashed:password-1", stored.passwordHash)
        assertEquals(role(centro, RolePreset.WAITER), stored.roleIdIn(centro.id))
        assertEquals("Waiter", anna.roleName)
    }

    @Test
    fun `email can be used only once, whatever the case`() {
        hire(centro, "anna@bistro.es", "Anna")

        assertFailsWith<ConflictException> { hire(playa, "ANNA@bistro.es", "Anna again") }
    }

    @Test
    fun `role of another venue is not found`() {
        assertFailsWith<NotFoundException> {
            service.hire(HireStaffCommand(centro.id, Email.of("anna@bistro.es"), "Anna", RawPassword.of("password-1"), role(playa, RolePreset.WAITER)))
        }
    }

    @Test
    fun `list shows only the staff of the venue, sorted by name, with role names`() {
        hire(centro, "pablo@bistro.es", "pablo", RolePreset.KITCHEN)
        hire(centro, "anna@bistro.es", "Anna")
        hire(playa, "lucia@bistro.es", "Lucia")

        val staff = service.list(centro.id)

        assertEquals(listOf("Anna" to "Waiter", "pablo" to "Kitchen"), staff.map { it.displayName to it.roleName })
    }

    @Test
    fun `change role is saved`() {
        val anna = hire(centro, "anna@bistro.es", "Anna")

        service.changeRole(ChangeStaffRoleCommand(centro.id, anna.userId, role(centro, RolePreset.MANAGER)))

        assertEquals(role(centro, RolePreset.MANAGER), users.stored.getValue(anna.userId).roleIdIn(centro.id))
    }

    @Test
    fun `nobody gives a role that allows more than their own`() {
        val owner = User.create(Email.of("owner@bistro.es"), "Owner", "hash").apply { becomeOwnerOf(tenant) }.also(users::save)
        val manager = hire(centro, "lucia@bistro.es", "Lucia", RolePreset.MANAGER, by = owner.id)
        val anna = hire(centro, "anna@bistro.es", "Anna", by = manager.userId)

        assertFailsWith<ForbiddenException> {
            hire(centro, "pablo@bistro.es", "Pablo", RolePreset.MANAGER, by = anna.userId)
        }
        assertFailsWith<ForbiddenException> {
            service.changeRole(ChangeStaffRoleCommand(centro.id, anna.userId, role(centro, RolePreset.MANAGER), anna.userId))
        }
        service.changeRole(ChangeStaffRoleCommand(centro.id, anna.userId, role(centro, RolePreset.KITCHEN), manager.userId))
        assertEquals(role(centro, RolePreset.KITCHEN), users.stored.getValue(anna.userId).roleIdIn(centro.id))
    }

    @Test
    fun `staff of another venue cannot be dismissed through this venue`() {
        val lucia = hire(playa, "lucia@bistro.es", "Lucia")

        assertFailsWith<NotFoundException> { service.dismiss(centro.id, lucia.userId) }
        assertEquals(role(playa, RolePreset.WAITER), users.stored.getValue(lucia.userId).roleIdIn(playa.id))
    }

    @Test
    fun `dismissed staff member keeps the account but loses the venue`() {
        val anna = hire(centro, "anna@bistro.es", "Anna")

        service.dismiss(centro.id, anna.userId)

        assertTrue(anna.userId in users.stored)
        assertTrue(service.list(centro.id).isEmpty())
    }

    @Test
    fun `unknown venue is not found`() {
        assertFailsWith<NotFoundException> { service.list(VenueId.new()) }
    }
}
