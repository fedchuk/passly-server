package com.passly.domain.discount

import com.passly.domain.common.ConflictException
import com.passly.domain.common.ValidationException
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DiscountListTest {

    private val tenantId = TenantId.new()
    private val list = DiscountList(VenueId.new(), emptyList())
    private val ten = DiscountPercent.of("10")

    @Test
    fun `discounts keep the order they were added in`() {
        list.add(tenantId, "Regular guest", ten)
        list.add(tenantId, "Staff meal", DiscountPercent.of("50"))

        assertEquals(listOf("Regular guest" to 0, "Staff meal" to 1), list.discounts.map { it.name to it.sortOrder })
        assertEquals("Staff meal · 50%", list.discounts.last().label)
    }

    @Test
    fun `names are unique without regard to case`() {
        val regular = list.add(tenantId, "Regular guest", ten)
        val staff = list.add(tenantId, "Staff meal", ten)

        assertFailsWith<ConflictException> { list.add(tenantId, " regular GUEST ", ten) }
        assertFailsWith<ConflictException> { list.change(staff.id, "Regular guest", ten) }
        list.change(regular.id, "regular guest", ten)
    }

    @Test
    fun `blank and too long names are refused`() {
        assertFailsWith<ValidationException> { list.add(tenantId, "  ", ten) }
        assertFailsWith<ValidationException> { list.add(tenantId, "x".repeat(VenueDiscount.MAX_NAME_LENGTH + 1), ten) }
    }

    @Test
    fun `a venue has at most twenty discounts`() {
        repeat(DiscountList.MAX_DISCOUNTS) { list.add(tenantId, "Discount $it", ten) }

        assertFailsWith<ValidationException> { list.add(tenantId, "One more", ten) }
    }

    @Test
    fun `discount changes its name and percent`() {
        val regular = list.add(tenantId, "Regular", ten)

        list.change(regular.id, "Regular guest", DiscountPercent.of("15"))

        assertEquals("Regular guest · 15%", list[regular.id].label)
    }

    @Test
    fun `removing a discount closes the gap in the order`() {
        val regular = list.add(tenantId, "Regular guest", ten)
        val staff = list.add(tenantId, "Staff meal", ten)

        val changed = list.remove(regular.id)

        assertEquals(setOf(staff), changed)
        assertEquals(listOf("Staff meal" to 0), list.discounts.map { it.name to it.sortOrder })
    }
}
