package com.passly.web.admin

import com.passly.application.discount.ChangeDiscountCommand
import com.passly.application.discount.CreateDiscountCommand
import com.passly.application.discount.DiscountService
import com.passly.domain.common.ConflictException
import com.passly.domain.common.ValidationException
import com.passly.domain.discount.VenueDiscountId
import com.passly.domain.venue.VenueId
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.util.*

@Controller
@RequestMapping("/admin/venues/{venueId}/settings/discounts")
class DiscountAdminController(private val discounts: DiscountService) {

    @PostMapping
    fun create(
        @PathVariable venueId: UUID,
        @RequestParam name: String,
        @RequestParam percent: String,
        flash: RedirectAttributes,
    ): String = flash.redirectAfter(venueId) {
        val discount = discounts.create(CreateDiscountCommand(VenueId(venueId), name, parseDiscountPercent(percent)))
        "Discount '${discount.name}' added"
    }

    @PostMapping("/{id}")
    fun change(
        @PathVariable venueId: UUID,
        @PathVariable id: UUID,
        @RequestParam name: String,
        @RequestParam percent: String,
        @RequestParam(required = false) version: Long?,
        flash: RedirectAttributes,
    ): String = flash.redirectAfter(venueId) {
        val discount = discounts.change(
            ChangeDiscountCommand(VenueId(venueId), VenueDiscountId(id), name, parseDiscountPercent(percent), version),
        )
        "Discount '${discount.name}' saved"
    }

    @PostMapping("/{id}/delete")
    fun delete(
        @PathVariable venueId: UUID,
        @PathVariable id: UUID,
        flash: RedirectAttributes,
    ): String = flash.redirectAfter(venueId) {
        val discount = discounts.delete(VenueId(venueId), VenueDiscountId(id))
        "Discount '${discount.name}' deleted"
    }

    private fun RedirectAttributes.redirectAfter(venueId: UUID, action: () -> String): String {
        try {
            addFlashAttribute("message", action())
        } catch (e: ValidationException) {
            addFlashAttribute("error", e.message)
        } catch (e: ConflictException) {
            addFlashAttribute("error", e.message)
        } catch (e: OptimisticLockingFailureException) {
            addFlashAttribute("error", "The discount was changed by someone else. Reload and try again.")
        }
        return "redirect:/admin/venues/$venueId/settings#discounts"
    }
}
