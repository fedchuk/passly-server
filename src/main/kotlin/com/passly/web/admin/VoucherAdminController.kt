package com.passly.web.admin

import com.passly.application.venue.VenueSettingsService
import com.passly.application.voucher.CreateVouchersCommand
import com.passly.application.voucher.VoucherService
import com.passly.domain.common.ConflictException
import com.passly.domain.common.NotFoundException
import com.passly.domain.common.ValidationException
import com.passly.domain.venue.VenueId
import com.passly.domain.voucher.VoucherId
import com.passly.security.PasslyUserPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.util.*

@Controller
@RequestMapping("/admin/venues/{venueId}/vouchers")
class VoucherAdminController(
    private val vouchers: VoucherService,
    private val settings: VenueSettingsService,
    private val venues: AdminVenues,
) {

    @GetMapping
    fun list(
        @PathVariable venueId: UUID,
        @AuthenticationPrincipal principal: PasslyUserPrincipal,
        model: Model,
    ): String {
        val today = vouchers.today(VenueId(venueId))
        val currency = settings.get(VenueId(venueId)).currency.value
        model.addAttribute("venue", venues.find(principal, venueId))
        model.addAttribute("vouchers", vouchers.list(VenueId(venueId)).map { it.toRowView(today, currency) })
        model.addAttribute("currency", currency)
        model.addAttribute("today", today.toString())
        model.addAttribute("suggestedValidUntil", today.plusMonths(3).toString())
        model.addAttribute("maxBatch", VoucherService.MAX_BATCH)
        model.addAttribute("listLimit", VoucherService.LIST_LIMIT)
        return "admin/vouchers"
    }

    @PostMapping
    fun create(
        @PathVariable venueId: UUID,
        @RequestParam name: String,
        @RequestParam kind: String,
        @RequestParam valueType: String,
        @RequestParam value: String,
        @RequestParam validUntil: String,
        @RequestParam(defaultValue = "1") quantity: Int,
        flash: RedirectAttributes,
    ): String {
        try {
            val created = vouchers.create(
                CreateVouchersCommand(
                    venueId = VenueId(venueId),
                    name = name,
                    kind = parseVoucherKind(kind),
                    value = parseVoucherValue(valueType, value),
                    validUntil = parseValidUntil(validUntil),
                    quantity = quantity,
                ),
            )
            val message = if (created.size == 1) "Voucher ${created.single().code.label} created" else "${created.size} vouchers created"
            flash.addFlashAttribute("message", message)
            flash.addFlashAttribute("createdCodes", created.map { it.code.label })
        } catch (e: ValidationException) {
            flash.addFlashAttribute("error", e.message)
        } catch (e: ConflictException) {
            flash.addFlashAttribute("error", e.message)
        }
        return "redirect:/admin/venues/$venueId/vouchers"
    }

    @PostMapping("/{id}/delete")
    fun delete(
        @PathVariable venueId: UUID,
        @PathVariable id: UUID,
        flash: RedirectAttributes,
    ): String {
        try {
            val voucher = vouchers.delete(VenueId(venueId), VoucherId(id))
            flash.addFlashAttribute("message", "Voucher ${voucher.code.label} deleted")
        } catch (e: ConflictException) {
            flash.addFlashAttribute("error", e.message)
        } catch (e: NotFoundException) {
            flash.addFlashAttribute("error", e.message)
        }
        return "redirect:/admin/venues/$venueId/vouchers"
    }
}
