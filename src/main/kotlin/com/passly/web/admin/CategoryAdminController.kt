package com.passly.web.admin

import com.passly.application.menu.*
import com.passly.domain.common.ConflictException
import com.passly.domain.common.ValidationException
import com.passly.domain.menu.CategoryId
import com.passly.domain.menu.MenuTree
import com.passly.domain.menu.StationId
import com.passly.domain.venue.VenueId
import com.passly.security.PasslyUserPrincipal
import jakarta.validation.Valid
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.util.*

@Controller
@RequestMapping("/admin/venues/{venueId}/categories")
class CategoryAdminController(
    private val categories: CategoryService,
    private val stations: StationService,
    private val venues: AdminVenues,
) {

    @PostMapping
    fun create(
        @PathVariable venueId: UUID,
        @Valid @ModelAttribute form: NewCategoryForm,
        binding: BindingResult,
        flash: RedirectAttributes,
    ): String {
        val target = form.parentId?.let { categoryPage(venueId, it) } ?: venuePage(venueId)
        if (binding.hasErrors()) {
            flash.addFlashAttribute("error", binding.allErrors.first().defaultMessage)
            return target
        }
        return flash.redirectAfter(target) {
            val category = categories.create(
                CreateCategoryCommand(
                    VenueId(venueId),
                    form.name,
                    form.parentId?.let(::CategoryId),
                    form.stationId?.let(::StationId),
                ),
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
        val tree = categories.getTree(VenueId(venueId))
        model.addAttribute("form", EditCategoryForm.of(tree[CategoryId(id)]))
        return editPage(venueId, id, tree, principal, model)
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
                return categoryPage(venueId, category.id.value)
            } catch (e: ConflictException) {
                binding.reject("conflict", e.message ?: "Conflict")
            } catch (e: OptimisticLockingFailureException) {
                binding.reject("conflict", "The category was changed by someone else. Reload and try again.")
            } catch (e: ValidationException) {
                binding.reject("invalid", e.message ?: "Invalid data")
            }
        }
        return editPage(venueId, id, categories.getTree(VenueId(venueId)), principal, model)
    }

    @PostMapping("/order")
    fun reorder(
        @PathVariable venueId: UUID,
        @RequestParam(required = false) parentId: UUID?,
        @RequestParam orderedIds: List<UUID>,
    ): ResponseEntity<Void> = reordered {
        categories.reorder(
            ReorderCategoriesCommand(
                VenueId(venueId),
                parentId?.let(::CategoryId),
                orderedIds.map(::CategoryId),
            ),
        )
    }

    @PostMapping("/{id}/delete")
    fun delete(
        @PathVariable venueId: UUID,
        @PathVariable id: UUID,
        flash: RedirectAttributes,
    ): String = flash.redirectAfter(parentPage(venueId, id)) {
        val name = categories.getTree(VenueId(venueId))[CategoryId(id)].name
        categories.delete(VenueId(venueId), CategoryId(id))
        "Category '$name' deleted"
    }

    @PostMapping("/{id}/move")
    fun move(
        @PathVariable venueId: UUID,
        @PathVariable id: UUID,
        @RequestParam(required = false) parentId: UUID?,
        flash: RedirectAttributes,
    ): String = flash.redirectAfter(venuePage(venueId)) {
        val category = categories.getTree(VenueId(venueId))[CategoryId(id)]
        val newParentId = parentId?.let(::CategoryId)
        if (category.parentId == newParentId) {
            "Category '${category.name}' is already there"
        } else {
            categories.move(MoveCategoryCommand(VenueId(venueId), category.id, newParentId))
            "Category '${category.name}' moved"
        }
    }

    private fun editPage(venueId: UUID, id: UUID, tree: MenuTree, principal: PasslyUserPrincipal, model: Model): String {
        val categoryId = CategoryId(id)
        val category = tree[categoryId]
        val venueStations = stations.list(VenueId(venueId))
        val inherited = category.parentId?.let { tree.effectiveStation(it) }
        model.addAttribute("venue", venues.find(principal, venueId))
        model.addAttribute("stations", venueStations.map { it.toOption() })
        model.addAttribute("topLevel", category.parentId == null)
        model.addAttribute("inheritedStation", venueStations.firstOrNull { it.id == inherited }?.name)
        model.addAttribute("categoryId", id)
        model.addAttribute("parentOptions", tree.parentOptionsFor(categoryId))
        model.addAttribute("currentParentId", tree[categoryId].parentId?.value)
        return "admin/category-edit"
    }

    private fun RedirectAttributes.redirectAfter(target: String, action: () -> String?): String {
        try {
            action()?.let { addFlashAttribute("message", it) }
        } catch (e: ConflictException) {
            addFlashAttribute("error", e.message)
        } catch (e: ValidationException) {
            addFlashAttribute("error", e.message)
        }
        return target
    }

    private fun parentPage(venueId: UUID, id: UUID): String =
        categories.getTree(VenueId(venueId))[CategoryId(id)].parentId
            ?.let { categoryPage(venueId, it.value) }
            ?: venuePage(venueId)

    private fun categoryPage(venueId: UUID, categoryId: UUID) = "redirect:/admin/venues/$venueId/categories/$categoryId/items"

    private fun venuePage(venueId: UUID) = "redirect:/admin/venues/$venueId"
}
