package com.passly.domain.role

import com.passly.domain.common.ConflictException
import com.passly.domain.common.NotFoundException
import com.passly.domain.common.ValidationException
import com.passly.domain.user.Permission
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId
import kotlin.test.*

class RoleListTest {

    private val tenant = TenantId.new()
    private val roles = RoleList(VenueId.new(), emptyList())

    @Test
    fun `presets are manager, waiter and kitchen and are added once`() {
        roles.addPresets(tenant)
        val again = roles.addPresets(tenant)

        assertEquals(listOf("Manager", "Waiter", "Kitchen"), roles.roles.map { it.name })
        assertTrue(again.isEmpty())
        val manager = roles.roles.first()
        assertFalse(manager.allows(Permission.MANAGE_STAFF))
        assertTrue(manager.allows(Permission.MANAGE_VENUE))
    }

    @Test
    fun `every role sees the menu and nobody but owners manages roles`() {
        val host = roles.add(tenant, "Host", setOf(Permission.TAKE_ORDERS))

        assertEquals(setOf(Permission.VIEW_MENU, Permission.TAKE_ORDERS), host.permissions)
        assertFailsWith<ValidationException> { roles.grant(host.id, setOf(Permission.MANAGE_ROLES)) }
        assertFailsWith<ValidationException> { roles.add(tenant, "Boss", setOf(Permission.MANAGE_ROLES)) }
    }

    @Test
    fun `grant replaces the permissions of a role`() {
        val host = roles.add(tenant, "Host", setOf(Permission.TAKE_ORDERS))

        roles.grant(host.id, setOf(Permission.CLOSE_ORDERS, Permission.MANAGE_TABLES))

        assertEquals(setOf(Permission.VIEW_MENU, Permission.CLOSE_ORDERS, Permission.MANAGE_TABLES), host.permissions)
    }

    @Test
    fun `names are unique in the venue whatever the case and owner is reserved`() {
        val host = roles.add(tenant, "Host", emptySet())
        roles.add(tenant, "Bartender", emptySet())

        assertFailsWith<ConflictException> { roles.add(tenant, " host ", emptySet()) }
        assertFailsWith<ConflictException> { roles.rename(host.id, "BARTENDER") }
        assertFailsWith<ValidationException> { roles.add(tenant, "owner", emptySet()) }
        assertFailsWith<ValidationException> { roles.add(tenant, "  ", emptySet()) }
        assertEquals("Hostess", roles.rename(host.id, " Hostess ").name)
    }

    @Test
    fun `removing a role closes the gap in the order`() {
        val first = roles.add(tenant, "First", emptySet())
        val second = roles.add(tenant, "Second", emptySet())

        val renumbered = roles.remove(first.id)

        assertEquals(setOf(second), renumbered)
        assertEquals(0, second.sortOrder)
        assertFailsWith<NotFoundException> { roles[first.id] }
    }

    @Test
    fun `a venue has a limited number of roles`() {
        repeat(RoleList.MAX_ROLES) { roles.add(tenant, "Role $it", emptySet()) }

        assertFailsWith<ValidationException> { roles.add(tenant, "One more", emptySet()) }
    }
}
