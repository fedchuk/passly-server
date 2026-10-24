package com.passly.domain.menu

import com.passly.domain.common.ConflictException
import com.passly.domain.common.NotFoundException
import com.passly.domain.common.ValidationException
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StationListTest {

    private val tenant = TenantId.new()
    private val list = StationList(VenueId.new(), emptyList())

    private fun names() = list.stations.map { it.name }

    @Test
    fun `stations are appended in order with unique names ignoring case`() {
        list.add(tenant, "Kitchen")
        val bar = list.add(tenant, " Bar ")

        assertEquals(listOf("Kitchen", "Bar"), names())
        assertEquals(1, bar.sortOrder)
        assertTrue(bar.isNew)
        assertFailsWith<ConflictException> { list.add(tenant, "kitchen") }
        assertFailsWith<ValidationException> { list.add(tenant, "  ") }
    }

    @Test
    fun `a station can change the case of its own name`() {
        val kitchen = list.add(tenant, "Kitchen")
        list.add(tenant, "Bar")

        assertEquals("KITCHEN", list.rename(kitchen.id, "KITCHEN").name)
        assertFailsWith<ConflictException> { list.rename(kitchen.id, "bar") }
    }

    @Test
    fun `shifting and removing keep positions without gaps`() {
        list.add(tenant, "Kitchen")
        list.add(tenant, "Bar")
        val grill = list.add(tenant, "Grill")

        list.shift(grill.id, -1)
        assertEquals(listOf("Kitchen", "Grill", "Bar"), names())
        assertTrue(list.shift(list.stations.first().id, -1).isEmpty())

        list.remove(list.stations.first().id)
        assertEquals(listOf("Grill", "Bar"), names())
        assertEquals(listOf(0, 1), list.stations.map { it.sortOrder })
    }

    @Test
    fun `the number of stations is limited`() {
        repeat(StationList.MAX_STATIONS) { list.add(tenant, "Station $it") }

        assertFailsWith<ValidationException> { list.add(tenant, "One more") }
        assertFailsWith<NotFoundException> { list.remove(StationId.new()) }
    }
}
