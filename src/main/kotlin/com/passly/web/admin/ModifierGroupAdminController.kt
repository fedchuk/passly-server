package com.passly.web.admin

import com.passly.application.menu.CreateModifierGroupCommand
import com.passly.application.menu.ModifierGroupService
import com.passly.application.menu.UpdateModifierGroupCommand
import com.passly.domain.common.ConflictException
import com.passly.domain.common.ValidationException
import com.passly.domain.menu.ModifierGroupId
import com.passly.domain.venue.VenueId
import com.passly.security.PasslyUserPrincipal
import jakarta.validation.Valid
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.util.UUID

@Controller
@RequestMapping("/admin/venues/{venueId}/modifier-groups")
class ModifierGroupAdminController(
    private val groups: ModifierGroupService,
    private val venues: AdminVenues,
) {

    @GetMapping
    fun list(
        @PathVariable venueId: UUID,
        @AuthenticationPrincipal principal: PasslyUserPrincipal,
        model: Model,
    ): String {
        model.addAttribute("venue", venues.find(principal, venueId))
        model.addAttribute("groups", groups.list(VenueId(venueId)).map { it.toRowView() })
        return "admin/modifier-groups"
    }

    @GetMapping("/new")
    fun newGroup(
        @PathVariable venueId: UUID,
        @AuthenticationPrincipal principal: PasslyUserPrincipal,
        model: Model,
    ): String {
        model.addAttribute("form", ModifierGroupForm.empty())
        return formPage(venueId, "New modifier group", "/admin/venues/$venueId/modifier-groups", principal, model)
    }

    @PostMapping
    fun create(
        @PathVariable venueId: UUID,
        @Valid @ModelAttribute("form") form: ModifierGroupForm,
        binding: BindingResult,
        @AuthenticationPrincipal principal: PasslyUserPrincipal,
        model: Model,
        flash: RedirectAttributes,
    ): String {
        if (!binding.hasErrors()) {
            try {
                val group = groups.create(
                    CreateModifierGroupCommand(
                        VenueId(venueId),
                        form.name,
                        form.minSelect,
                        form.maxSelect,
                        form.drafts()
                    ),
                )
                flash.addFlashAttribute("message", "Modifier group '${group.name}' added")
                return listPage(venueId)
            } catch (e: ValidationException) {
                binding.reject("invalid", e.message ?: "Invalid data")
            }
        }
        return formPage(venueId, "New modifier group", "/admin/venues/$venueId/modifier-groups", principal, model)
    }

    @GetMapping("/{id}/edit")
    fun edit(
        @PathVariable venueId: UUID,
        @PathVariable id: UUID,
        @AuthenticationPrincipal principal: PasslyUserPrincipal,
        model: Model,
    ): String {
        val group = groups.get(VenueId(venueId), ModifierGroupId(id))
        model.addAttribute("form", ModifierGroupForm.of(group))
        return formPage(venueId, "Edit ${group.name}", "/admin/venues/$venueId/modifier-groups/$id", principal, model)
    }

    @PostMapping("/{id}")
    fun update(
        @PathVariable venueId: UUID,
        @PathVariable id: UUID,
        @Valid @ModelAttribute("form") form: ModifierGroupForm,
        binding: BindingResult,
        @AuthenticationPrincipal principal: PasslyUserPrincipal,
        model: Model,
        flash: RedirectAttributes,
    ): String {
        if (!binding.hasErrors()) {
            try {
                val group = groups.update(
                    UpdateModifierGroupCommand(
                        venueId = VenueId(venueId),
                        groupId = ModifierGroupId(id),
                        name = form.name,
                        minSelect = form.minSelect,
                        maxSelect = form.maxSelect,
                        options = form.drafts(),
                        expectedVersion = form.version,
                    ),
                )
                flash.addFlashAttribute("message", "Modifier group '${group.name}' saved")
                return listPage(venueId)
            } catch (e: ConflictException) {
                binding.reject("conflict", e.message ?: "Conflict")
            } catch (e: OptimisticLockingFailureException) {
                binding.reject("conflict", "The group was changed by someone else. Reload and try again.")
            } catch (e: ValidationException) {
                binding.reject("invalid", e.message ?: "Invalid data")
            }
        }
        return formPage(venueId, "Edit ${form.name}", "/admin/venues/$venueId/modifier-groups/$id", principal, model)
    }

    @PostMapping("/{id}/delete")
    fun delete(
        @PathVariable venueId: UUID,
        @PathVariable id: UUID,
        flash: RedirectAttributes,
    ): String {
        val venue = VenueId(venueId)
        val group = groups.get(venue, ModifierGroupId(id))
        try {
            groups.delete(venue, group.id)
            flash.addFlashAttribute("message", "Modifier group '${group.name}' deleted")
        } catch (e: ConflictException) {
            flash.addFlashAttribute("error", e.message)
        }
        return listPage(venueId)
    }

    private fun formPage(
        venueId: UUID,
        title: String,
        action: String,
        principal: PasslyUserPrincipal,
        model: Model,
    ): String {
        model.addAttribute("venue", venues.find(principal, venueId))
        model.addAttribute("formTitle", title)
        model.addAttribute("formAction", action)
        return "admin/modifier-group-form"
    }

    private fun listPage(venueId: UUID) = "redirect:/admin/venues/$venueId/modifier-groups"
}
