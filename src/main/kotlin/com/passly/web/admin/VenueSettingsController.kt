package com.passly.web.admin

import com.passly.application.venue.UpdateVenueSettingsCommand
import com.passly.application.venue.VenueSettingsService
import com.passly.domain.common.ConflictException
import com.passly.domain.common.ValidationException
import com.passly.domain.venue.CountryCode
import com.passly.domain.venue.CurrencyCode
import com.passly.domain.venue.VenueId
import com.passly.security.PasslyUserPrincipal
import jakarta.validation.Valid
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.util.*

@Controller
@RequestMapping("/admin/venues/{venueId}/settings")
class VenueSettingsController(
    private val settings: VenueSettingsService,
    private val venues: AdminVenues,
) {

    @GetMapping
    fun show(
        @PathVariable venueId: UUID,
        @AuthenticationPrincipal principal: PasslyUserPrincipal,
        model: Model,
    ): String {
        model.addAttribute("form", VenueSettingsForm.of(settings.get(VenueId(venueId))))
        return page(venueId, principal, model)
    }

    @PostMapping
    fun update(
        @PathVariable venueId: UUID,
        @Valid @ModelAttribute("form") form: VenueSettingsForm,
        binding: BindingResult,
        @AuthenticationPrincipal principal: PasslyUserPrincipal,
        model: Model,
        flash: RedirectAttributes,
    ): String {
        if (!binding.hasErrors()) {
            try {
                settings.update(
                    UpdateVenueSettingsCommand(
                        venueId = VenueId(venueId),
                        country = CountryCode.of(form.country),
                        currency = CurrencyCode.of(form.currency),
                        expectedVersion = form.version,
                    ),
                )
                flash.addFlashAttribute("message", "Settings saved")
                return "redirect:/admin/venues/$venueId/settings"
            } catch (e: ValidationException) {
                if (e.field != null) binding.rejectValue(e.field, "invalid", e.message ?: "Invalid value")
                else binding.reject("invalid", e.message ?: "Invalid data")
            } catch (e: ConflictException) {
                binding.reject("conflict", e.message ?: "Conflict")
            } catch (e: OptimisticLockingFailureException) {
                binding.reject("conflict", "Settings were changed by someone else. Reload and try again.")
            }
        }
        return page(venueId, principal, model)
    }

    private fun page(venueId: UUID, principal: PasslyUserPrincipal, model: Model): String {
        model.addAttribute("venue", venues.find(principal, venueId))
        model.addAttribute("countries", countryOptions())
        model.addAttribute("currencies", currencyOptions())
        model.addAttribute("currencyLocked", settings.currencyLocked(VenueId(venueId)))
        return "admin/settings"
    }
}
