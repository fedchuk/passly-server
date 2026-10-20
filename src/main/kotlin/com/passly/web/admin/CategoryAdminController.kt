package com.passly.web.admin

import com.passly.application.menu.CategoryService
import com.passly.application.menu.CreateCategoryCommand
import com.passly.application.menu.UpdateCategoryCommand
import com.passly.domain.common.ConflictException
import com.passly.domain.common.ValidationException
import com.passly.domain.menu.CategoryId
import com.passly.domain.menu.StationId
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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.util.UUID

@Controller
@RequestMapping("/admin/venues/{venueId}/categories")
class CategoryAdminController(
    private val categories: CategoryService,
    private val venues: AdminVenues,
) {

    @PostMapping
    fun create(
        @PathVariable venueId: UUID,
        @Valid @ModelAttribute form: NewCategoryForm,
        binding: BindingResult,
        flash: RedirectAttributes,
    ): String {
        if (binding.hasErrors()) {
            flash.addFlashAttribute("error", binding.allErrors.first().defaultMessage)
            return venuePage(venueId)
        }
        return flash.redirectAfter(venueId) {
            val category = categories.create(
                CreateCategoryCommand(VenueId(venueId), form.name, form.parentId?.let(::CategoryId)),
            )
            "Category '${category.name}' added"
        }
    }

    @GetMapping("/{id}/edit")
    fun edit(
        @PathVariable venueId: UUID,
        @PathVariable id: UUID,
        @AuthenticationPrincipal principal: PasslyUserPrincipal,
        model: Model,
    ): String {
        val category = categories.getTree(VenueId(venueId))[CategoryId(id)]
        model.addAttribute("form", EditCategoryForm.of(category))
        return editPage(venueId, id, principal, model)
    }

    @PostMapping("/{id}")
    fun update(
        @PathVariable venueId: UUID,
        @PathVariable id: UUID,
        @Valid @ModelAttribute("form") form: EditCategoryForm,
        binding: BindingResult,
        @AuthenticationPrincipal principal: PasslyUserPrincipal,
        model: Model,
        flash: RedirectAttributes,
    ): String {
        if (!binding.hasErrors()) {
            try {
                val category = categories.update(
                    UpdateCategoryCommand(
                        venueId = VenueId(venueId),
                        categoryId = CategoryId(id),
                        name = form.name,
                        stationId = form.stationId?.let(::StationId),
                        active = form.active,
                        expectedVersion = form.version,
                    ),
                )
                flash.addFlashAttribute("message", "Category '${category.name}' saved")
                return venuePage(venueId)
            } catch (e: ConflictException) {
                binding.reject("conflict", e.message ?: "Conflict")
            } catch (e: OptimisticLockingFailureException) {
                binding.reject("conflict", "The category was changed by someone else. Reload and try again.")
            } catch (e: ValidationException) {
                binding.reject("invalid", e.message ?: "Invalid data")
            }
        }
        return editPage(venueId, id, principal, model)
    }

    @PostMapping("/{id}/shift")
    fun shift(
        @PathVariable venueId: UUID,
        @PathVariable id: UUID,
        @RequestParam delta: Int,
        flash: RedirectAttributes,
    ): String = flash.redirectAfter(venueId) {
        if (delta != -1 && delta != 1) throw ValidationException("A category moves one step at a time")
        categories.shift(VenueId(venueId), CategoryId(id), delta)
        null
    }

    @PostMapping("/{id}/delete")
    fun delete(
        @PathVariable venueId: UUID,
        @PathVariable id: UUID,
        flash: RedirectAttributes,
    ): String = flash.redirectAfter(venueId) {
        val name = categories.getTree(VenueId(venueId))[CategoryId(id)].name
        categories.delete(VenueId(venueId), CategoryId(id))
        "Category '$name' deleted"
    }

    private fun editPage(venueId: UUID, id: UUID, principal: PasslyUserPrincipal, model: Model): String {
        model.addAttribute("venue", venues.find(principal, venueId))
        model.addAttribute("categoryId", id)
        return "admin/category-edit"
    }

    private fun RedirectAttributes.redirectAfter(venueId: UUID, action: () -> String?): String {
        try {
            action()?.let { addFlashAttribute("message", it) }
        } catch (e: ConflictException) {
            addFlashAttribute("error", e.message)
        } catch (e: ValidationException) {
            addFlashAttribute("error", e.message)
        }
        return venuePage(venueId)
    }

    private fun venuePage(venueId: UUID) = "redirect:/admin/venues/$venueId"
}
