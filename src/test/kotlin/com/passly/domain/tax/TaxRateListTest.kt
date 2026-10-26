package com.passly.domain.tax

import com.passly.domain.common.ConflictException
import com.passly.domain.common.TaxRate
import com.passly.domain.common.ValidationException
import com.passly.domain.venue.CountryCode
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TaxRateListTest {

    private val tenantId = TenantId.new()
    private val list = TaxRateList(VenueId.new(), emptyList())

    @Test
    fun `rates keep the order they were added in`() {
        list.add(tenantId, "Food", TaxRate.of("10"))
        list.add(tenantId, "Alcohol", TaxRate.of("20"))

        assertEquals(listOf("Food" to 0, "Alcohol" to 1), list.rates.map { it.name to it.sortOrder })
    }

    @Test
    fun `names are unique without regard to case`() {
        list.add(tenantId, "Alcohol", TaxRate.of("20"))

        assertFailsWith<ConflictException> { list.add(tenantId, " alcohol ", TaxRate.of("10")) }
    }

    @Test
    fun `blank name is refused`() {
        assertFailsWith<ValidationException> { list.add(tenantId, "  ", TaxRate.of("10")) }
    }

    @Test
    fun `a venue has at most ten rates`() {
        repeat(TaxRateList.MAX_RATES) { list.add(tenantId, "Rate $it", TaxRate.of("10")) }

        assertFailsWith<ValidationException> { list.add(tenantId, "One more", TaxRate.of("10")) }
    }

    @Test
    fun `rate changes its name and percent`() {
        val food = list.add(tenantId, "Food", TaxRate.of("10"))

        list.change(food.id, "Food on site", TaxRate.of("10.5"))

        assertEquals("Food on site", list[food.id].name)
        assertEquals("10.5%", list[food.id].percentLabel)
    }

    @Test
    fun `removing a rate closes the gap in the order`() {
        val food = list.add(tenantId, "Food", TaxRate.of("10"))
        val alcohol = list.add(tenantId, "Alcohol", TaxRate.of("20"))

        val changed = list.remove(food.id)

        assertEquals(setOf(alcohol), changed)
        assertEquals(listOf("Alcohol" to 0), list.rates.map { it.name to it.sortOrder })
    }

    @Test
    fun `presets add only the rates the venue does not have yet`() {
        list.add(tenantId, "alcohol", TaxRate.of("20"))

        val added = list.addMissing(tenantId, TaxRatePreset.forCountry(CountryCode.of("FR")))

        assertEquals(listOf("Food and soft drinks", "Reduced"), added.map { it.name })
        assertEquals(3, list.rates.size)
    }

    @Test
    fun `country without presets adds nothing`() {
        assertTrue(TaxRatePreset.forCountry(CountryCode.of("JP")).isEmpty())
        assertEquals(listOf("8.1%", "2.6%"), TaxRatePreset.forCountry(CountryCode.of("CH")).map { "${it.rate.percent.stripTrailingZeros().toPlainString()}%" })
    }
}
