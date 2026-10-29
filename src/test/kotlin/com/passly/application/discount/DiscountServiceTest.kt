package com.passly.application.discount

import com.passly.application.menu.FakeVenueRepository
import com.passly.domain.common.ConflictException
import com.passly.domain.common.NotFoundException
import com.passly.domain.discount.DiscountPercent
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.Venue
import com.passly.domain.venue.VenueId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DiscountServiceTest {

    private val venue = Venue(VenueId.new(), TenantId.new(), "Bistro")
    private val venues = FakeVenueRepository(venue)
    private val discounts = FakeDiscountRepository()
    private val service = DiscountService(venues, discounts)
    private val ten = DiscountPercent.of("10")

    @Test
    fun `discount is added under a lock of the venue`() {
        val regular = service.create(CreateDiscountCommand(venue.id, "Regular guest", ten))

        assertEquals(listOf("Regular guest · 10%"), service.list(venue.id).map { it.label })
        assertEquals(venue.tenantId, regular.tenantId)
        assertEquals(1, venues.locks)
    }

    @Test
    fun `discount is changed and a stale form is refused`() {
        val regular = service.create(CreateDiscountCommand(venue.id, "Regular", ten))
        val stored = service.list(venue.id).single()

        service.change(ChangeDiscountCommand(venue.id, regular.id, "Regular guest", DiscountPercent.of("15"), stored.version))

        assertEquals("Regular guest · 15%", service.list(venue.id).single().label)
        assertFailsWith<ConflictException> {
            service.change(ChangeDiscountCommand(venue.id, regular.id, "Regular", ten, stored.version))
        }
    }

    @Test
    fun `deleting a discount renumbers the rest`() {
        val regular = service.create(CreateDiscountCommand(venue.id, "Regular guest", ten))
        service.create(CreateDiscountCommand(venue.id, "Staff meal", DiscountPercent.of("50")))

        service.delete(venue.id, regular.id)

        assertEquals(listOf("Staff meal" to 0), service.list(venue.id).map { it.name to it.sortOrder })
        assertFailsWith<NotFoundException> { service.delete(venue.id, regular.id) }
    }

    @Test
    fun `unknown venue is not found`() {
        assertFailsWith<NotFoundException> { service.list(VenueId.new()) }
        assertFailsWith<NotFoundException> { service.create(CreateDiscountCommand(VenueId.new(), "Regular", ten)) }
    }
}
