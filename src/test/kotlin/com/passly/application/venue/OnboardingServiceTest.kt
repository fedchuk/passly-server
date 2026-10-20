package com.passly.application.venue

import com.passly.application.menu.FakeVenueRepository
import com.passly.application.user.FakePasswordHasher
import com.passly.application.user.FakeUserRepository
import com.passly.domain.common.ConflictException
import com.passly.domain.common.ValidationException
import com.passly.domain.user.Email
import com.passly.domain.user.RawPassword
import com.passly.domain.user.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OnboardingServiceTest {

    private val tenants = FakeTenantRepository()
    private val venues = FakeVenueRepository()
    private val users = FakeUserRepository()
    private val service = OnboardingService(tenants, venues, users, FakePasswordHasher())

    private fun register(email: String = "max@bistro.es", business: String = "Bistro Group", venue: String = "Centro") =
        service.registerBusiness(
            RegisterBusinessCommand(business, venue, Email.of(email), "Max", RawPassword.of("password-1")),
        )

    @Test
    fun `new business gets a venue and an owner`() {
        val business = register()

        assertEquals("Bistro Group", tenants.stored.getValue(business.tenantId).name)
        val venue = venues.findById(business.venueId)!!
        assertEquals("Centro", venue.name)
        assertEquals(business.tenantId, venue.tenantId)
        val owner = users.stored.getValue(business.ownerId)
        assertEquals(Role.OWNER, owner.roleIn(venue))
        assertEquals("hashed:password-1", owner.passwordHash)
    }

    @Test
    fun `owner email must be free`() {
        register()

        assertFailsWith<ConflictException> { register(business = "Another group") }
        assertEquals(1, tenants.stored.size)
    }

    @Test
    fun `names are validated before anything is saved`() {
        assertFailsWith<ValidationException> { register(business = "  ") }
        assertFailsWith<ValidationException> { register(venue = "") }

        assertTrue(tenants.stored.isEmpty())
        assertTrue(users.stored.isEmpty())
    }
}
