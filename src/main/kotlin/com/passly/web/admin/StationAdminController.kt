package com.passly.web.admin

import com.passly.application.menu.CreateStationCommand
import com.passly.application.menu.RenameStationCommand
import com.passly.application.menu.StationService
import com.passly.domain.common.ConflictException
import com.passly.domain.common.ValidationException
import com.passly.domain.menu.StationId
import com.passly.domain.venue.VenueId
import com.passly.security.PasslyUserPrincipal
import jakarta.validation.Valid
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.util.*

@Controller
@RequestMapping("/admin/venues/{venueId}/stations")
class StationAdminController(
    private val stations: StationService,
    private val venues: AdminVenues,
    private val messages: MessageSource,
) {

    @GetMapping
    fun list(
        @PathVariable venueId: UUID,
        @AuthenticationPrincipal principal: PasslyUserPrincipal,
        model: Model,
    ): String {
        model.addAttribute("venue", venues.find(principal, venueId))
        model.addAttribute("stations", stations.list(VenueId(venueId)).toRowViews())
        return "admin/stations"
    }

    @PostMapping
    fun create(
        @PathVariable venueId: UUID,
        @Valid @ModelAttribute form: StationForm,
        binding: BindingResult,
        flash: RedirectAttributes,
    ): String = flash.redirectAfter(venueId, binding) {
        val station = stations.create(CreateStationCommand(VenueId(venueId), form.name))
        "Station '${station.name}' added"
    }

    @PostMapping("/{id}/rename")
    fun rename(
        @PathVariable venueId: UUID,
        @PathVariable id: UUID,
        @Valid @ModelAttribute form: StationForm,
        binding: BindingResult,
        flash: RedirectAttributes,
    ): String = flash.redirectAfter(venueId, binding) {
        val station = stations.rename(RenameStationCommand(VenueId(venueId), StationId(id), form.name, form.version))
        "Station renamed to '${station.name}'"
    }

    @PostMapping("/{id}/shift")
    fun shift(
        @PathVariable venueId: UUID,
        @PathVariable id: UUID,
        @RequestParam delta: Int,
        flash: RedirectAttributes,
    ): String = flash.redirectAfter(venueId) {
        if (delta != -1 && delta != 1) throw ValidationException("Move one step at a time")
        stations.shift(VenueId(venueId), StationId(id), delta)
        null
    }

    @PostMapping("/{id}/delete")
    fun delete(
        @PathVariable venueId: UUID,
        @PathVariable id: UUID,
        flash: RedirectAttributes,
    ): String = flash.redirectAfter(venueId) {
        val name = stations.list(VenueId(venueId)).firstOrNull { it.id.value == id }?.name
        stations.delete(VenueId(venueId), StationId(id))
        "Station '$name' deleted"
    }

    private fun RedirectAttributes.redirectAfter(
        venueId: UUID,
        binding: BindingResult? = null,
        action: () -> String?,
    ): String {
        val bindingError = binding?.allErrors?.firstOrNull()
        if (bindingError != null) {
            addFlashAttribute("error", messages.getMessage(bindingError, LocaleContextHolder.getLocale()))
            return stationsPage(venueId)
        }
        try {
            action()?.let { addFlashAttribute("message", it) }
        } catch (e: ConflictException) {
            addFlashAttribute("error", e.message)
        } catch (e: ValidationException) {
            addFlashAttribute("error", e.message)
        } catch (e: OptimisticLockingFailureException) {
            addFlashAttribute("error", "The station was changed by someone else. Reload and try again.")
        }
        return stationsPage(venueId)
    }

    private fun stationsPage(venueId: UUID) = "redirect:/admin/venues/$venueId/stations"
}