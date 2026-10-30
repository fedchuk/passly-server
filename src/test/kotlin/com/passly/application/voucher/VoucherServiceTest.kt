package com.passly.application.voucher

import com.passly.application.menu.FakeVenueRepository
import com.passly.application.venue.FakeVenueSettingsRepository
import com.passly.domain.common.ConflictException
import com.passly.domain.common.Money
import com.passly.domain.common.NotFoundException
import com.passly.domain.common.ValidationException
import com.passly.domain.discount.DiscountPercent
import com.passly.domain.order.OrderId
import com.passly.domain.venue.*
import com.passly.domain.voucher.VoucherId
import com.passly.domain.voucher.VoucherKind
import com.passly.domain.voucher.VoucherValue
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VoucherServiceTest {

    private val venue = Venue(VenueId.new(), TenantId.new(), "Bistro")
    private val now = Instant.parse("2026-10-30T23:30:00Z")
    private val venues = FakeVenueRepository(venue)
    private val settings = FakeVenueSettingsRepository(
        VenueSettings.fromStorage(venue.id, CountryCode.of("ES"), CurrencyCode.of("EUR"), 0, timeZone = ZoneId.of("Europe/Madrid")),
    )
    private val vouchers = FakeVoucherRepository()
    private val service = VoucherService(venues, settings, vouchers, Clock.fixed(now, ZoneOffset.UTC))
    private val gift = VoucherValue.Amount(Money.of("25.00"))

    private fun create(kind: VoucherKind = VoucherKind.SINGLE_USE, quantity: Int = 1, validUntil: String = "2026-12-31") =
        service.create(CreateVouchersCommand(venue.id, "Gift card", kind, gift, LocalDate.parse(validUntil), quantity))

    @Test
    fun `today is the day of the venue`() {
        assertEquals(LocalDate.parse("2026-10-31"), service.today(venue.id))
        assertFailsWith<ValidationException> { create(validUntil = "2026-10-30") }
    }

    @Test
    fun `single-use vouchers are created in a batch with distinct codes`() {
        val created = create(quantity = 20)

        assertEquals(20, created.map { it.code }.toSet().size)
        assertEquals(20, vouchers.stored.size)
        assertTrue(created.all { it.tenantId == venue.tenantId && it.issuedAt == now })
    }

    @Test
    fun `reusable voucher is created alone and batches are limited`() {
        assertFailsWith<ValidationException> { create(VoucherKind.REUSABLE, quantity = 2) }
        assertFailsWith<ValidationException> { create(quantity = VoucherService.MAX_BATCH + 1) }
        assertFailsWith<ValidationException> { create(quantity = 0) }

        val newsletter = service.create(
            CreateVouchersCommand(venue.id, "Newsletter", VoucherKind.REUSABLE, VoucherValue.Percent(DiscountPercent.of("15")), LocalDate.parse("2027-01-31")),
        ).single()

        assertEquals("Newsletter", service.list(venue.id).single().name)
        assertEquals(VoucherKind.REUSABLE, newsletter.kind)
    }

    @Test
    fun `used voucher stays, unused one is deleted`() {
        val (used, unused) = create(quantity = 2)
        val stored = vouchers.findForUpdate(venue.id, used.id)!!
        stored.redeem(OrderId.new(), now, LocalDate.parse("2026-10-31"))
        vouchers.save(stored)

        assertFailsWith<ConflictException> { service.delete(venue.id, used.id) }
        service.delete(venue.id, unused.id)

        assertEquals(listOf(used.id), vouchers.stored.keys.toList())
        assertFailsWith<NotFoundException> { service.delete(venue.id, VoucherId.new()) }
    }
}
