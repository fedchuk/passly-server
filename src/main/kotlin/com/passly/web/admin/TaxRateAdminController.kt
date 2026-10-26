package com.passly.web.admin

import com.passly.application.tax.ChangeTaxRateCommand
import com.passly.application.tax.CreateTaxRateCommand
import com.passly.application.tax.TaxRateService
import com.passly.domain.common.ConflictException
import com.passly.domain.common.ValidationException
import com.passly.domain.tax.VenueTaxRateId
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
@RequestMapping("/admin/venues/{venueId}/settings/tax-rates")
class TaxRateAdminController(private val taxRates: TaxRateService) {

    @PostMapping
    fun create(
        @PathVariable venueId: UUID,
        @RequestParam name: String,
        @RequestParam rate: String,
        flash: RedirectAttributes,
    ): String = flash.redirectAfter(venueId) {
        val taxRate = taxRates.create(CreateTaxRateCommand(VenueId(venueId), name, parseTaxRate(rate)))
        "Tax rate '${taxRate.name}' added"
    }

    @PostMapping("/presets")
    fun addPresets(
        @PathVariable venueId: UUID,
        flash: RedirectAttributes,
    ): String = flash.redirectAfter(venueId) {
        val added = taxRates.addPresets(VenueId(venueId))
        if (added.isEmpty()) "The usual rates are already there" else "Added ${added.joinToString(", ") { it.name }}"
    }

    @PostMapping("/{id}")
    fun change(
        @PathVariable venueId: UUID,
        @PathVariable id: UUID,
        @RequestParam name: String,
        @RequestParam rate: String,
        @RequestParam(required = false) version: Long?,
        flash: RedirectAttributes,
    ): String = flash.redirectAfter(venueId) {
        val taxRate = taxRates.change(
            ChangeTaxRateCommand(
                VenueId(venueId),
                VenueTaxRateId(id),
                name,
                parseTaxRate(rate),
                version
            )
        )
        "Tax rate '${taxRate.name}' saved"
    }

    @PostMapping("/{id}/delete")
    fun delete(
        @PathVariable venueId: UUID,
        @PathVariable id: UUID,
        flash: RedirectAttributes,
    ): String = flash.redirectAfter(venueId) {
        val taxRate = taxRates.delete(VenueId(venueId), VenueTaxRateId(id))
        "Tax rate '${taxRate.name}' deleted"
    }

    private fun RedirectAttributes.redirectAfter(venueId: UUID, action: () -> String): String {
        try {
            addFlashAttribute("message", action())
        } catch (e: ValidationException) {
            addFlashAttribute("error", e.message)
        } catch (e: ConflictException) {
            addFlashAttribute("error", e.message)
        } catch (e: OptimisticLockingFailureException) {
            addFlashAttribute("error", "The tax rate was changed by someone else. Reload and try again.")
        }
        return "redirect:/admin/venues/$venueId/settings#tax-rates"
    }
}
