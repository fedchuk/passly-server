package com.passly.web.admin

import com.passly.application.menu.AttachModifierGroupsCommand
import com.passly.application.menu.CategoryService
import com.passly.application.menu.CreateMenuItemCommand
import com.passly.application.menu.MenuItemService
import com.passly.application.menu.ModifierGroupService
import com.passly.application.menu.MoveMenuItemCommand
import com.passly.application.menu.StationService
import com.passly.application.menu.UpdateMenuItemCommand
import com.passly.domain.common.ConflictException
import com.passly.domain.common.ValidationException
import com.passly.domain.menu.CategoryId
import com.passly.domain.menu.MenuItemId
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
@RequestMapping("/admin/venues/{venueId}")
class MenuItemAdminController(
    private val items: MenuItemService,
    private val categories: CategoryService,
    private val stations: StationService,
    private val modifierGroups: ModifierGroupService,
    private val venues: AdminVenues,
) {

    @GetMapping("/categories/{categoryId}/items")
    fun list(
        @PathVariable venueId: UUID,
        @PathVariable categoryId: UUID,
        @AuthenticationPrincipal principal: PasslyUserPrincipal,
        model: Model,
    ): String {
        val venue = VenueId(venueId)
        val tree = categories.getTree(venue)
        val category = tree[CategoryId(categoryId)]
        model.addAttribute("venue", venues.find(principal, venueId))
        model.addAttribute("category", CategoryRefView(category.id.value, category.name))
        model.addAttribute("items", items.listInCategory(venue, category.id).toRowViews(tree, stations.list(venue)))
        return "admin/items"
    }

    @GetMapping("/categories/{categoryId}/items/new")
    fun newItem(
        @PathVariable venueId: UUID,
        @PathVariable categoryId: UUID,
        @AuthenticationPrincipal principal: PasslyUserPrincipal,
        model: Model,
    ): String {
        model.addAttribute("form", MenuItemForm())
        return newPage(venueId, categoryId, principal, model)
    }

    @PostMapping("/categories/{categoryId}/items")
    fun create(
        @PathVariable venueId: UUID,
        @PathVariable categoryId: UUID,
        @Valid @ModelAttribute("form") form: MenuItemForm,
        binding: BindingResult,
        @AuthenticationPrincipal principal: PasslyUserPrincipal,
        model: Model,
        flash: RedirectAttributes,
    ): String {
        if (!binding.hasErrors()) {
            try {
                val venue = VenueId(venueId)
                val item = items.create(CreateMenuItemCommand(venue, CategoryId(categoryId), form.details()))
                if (form.modifierGroupIds.isNotEmpty()) {
                    items.attachModifierGroups(AttachModifierGroupsCommand(venue, item.id, form.groupIds()))
                }
                flash.addFlashAttribute("message", "'${item.name}' added")
                return listPage(venueId, categoryId)
            } catch (e: ValidationException) {
                binding.reject("invalid", e.message ?: "Invalid data")
            }
        }
        return newPage(venueId, categoryId, principal, model)
    }

    @GetMapping("/items/{id}/edit")
    fun edit(
        @PathVariable venueId: UUID,
        @PathVariable id: UUID,
        @AuthenticationPrincipal principal: PasslyUserPrincipal,
        model: Model,
    ): String {
        val item = items.get(VenueId(venueId), MenuItemId(id))
        model.addAttribute("form", MenuItemForm.of(item))
        return editPage(venueId, id, principal, model)
    }

    @PostMapping("/items/{id}")
    fun update(
        @PathVariable venueId: UUID,
        @PathVariable id: UUID,
        @Valid @ModelAttribute("form") form: MenuItemForm,
        binding: BindingResult,
        @AuthenticationPrincipal principal: PasslyUserPrincipal,
        model: Model,
        flash: RedirectAttributes,
    ): String {
        if (!binding.hasErrors()) {
            try {
                val venue = VenueId(venueId)
                val item = items.update(UpdateMenuItemCommand(venue, MenuItemId(id), form.details(), form.version))
                if (item.modifierGroupIds != form.groupIds()) {
                    items.attachModifierGroups(AttachModifierGroupsCommand(venue, item.id, form.groupIds()))
                }
                flash.addFlashAttribute("message", "'${item.name}' saved")
                return listPage(venueId, checkNotNull(item.categoryId).value)
            } catch (e: ConflictException) {
                binding.reject("conflict", e.message ?: "Conflict")
            } catch (e: OptimisticLockingFailureException) {
                binding.reject("conflict", "The item was changed by someone else. Reload and try again.")
            } catch (e: ValidationException) {
                binding.reject("invalid", e.message ?: "Invalid data")
            }
        }
        return editPage(venueId, id, principal, model)
    }

    @PostMapping("/items/{id}/availability")
    fun changeAvailability(
        @PathVariable venueId: UUID,
        @PathVariable id: UUID,
        @RequestParam available: Boolean,
        flash: RedirectAttributes,
    ): String {
        val item = items.changeAvailability(VenueId(venueId), MenuItemId(id), available)
        val state = if (available) "is back on the menu" else "is on the stop list"
        flash.addFlashAttribute("message", "'${item.name}' $state")
        return listPage(venueId, checkNotNull(item.categoryId).value)
    }

    @PostMapping("/items/{id}/shift")
    fun shift(
        @PathVariable venueId: UUID,
        @PathVariable id: UUID,
        @RequestParam delta: Int,
        flash: RedirectAttributes,
    ): String {
        val venue = VenueId(venueId)
        val categoryId = checkNotNull(items.get(venue, MenuItemId(id)).categoryId).value
        if (delta == -1 || delta == 1) {
            items.shift(venue, MenuItemId(id), delta)
        } else {
            flash.addFlashAttribute("error", "An item moves one step at a time")
        }
        return listPage(venueId, categoryId)
    }

    @PostMapping("/items/{id}/archive")
    fun archive(
        @PathVariable venueId: UUID,
        @PathVariable id: UUID,
        flash: RedirectAttributes,
    ): String {
        val venue = VenueId(venueId)
        val item = items.get(venue, MenuItemId(id))
        items.archive(venue, item.id)
        flash.addFlashAttribute("message", "'${item.name}' archived")
        return listPage(venueId, checkNotNull(item.categoryId).value)
    }

    @PostMapping("/items/{id}/move")
    fun move(
        @PathVariable venueId: UUID,
        @PathVariable id: UUID,
        @RequestParam categoryId: UUID,
        flash: RedirectAttributes,
    ): String {
        val venue = VenueId(venueId)
        val item = items.get(venue, MenuItemId(id))
        val target = categories.getTree(venue)[CategoryId(categoryId)]
        if (item.categoryId == target.id) {
            flash.addFlashAttribute("message", "'${item.name}' is already in ${target.name}")
        } else {
            items.move(MoveMenuItemCommand(venue, item.id, target.id))
            flash.addFlashAttribute("message", "'${item.name}' moved to ${target.name}")
        }
        return listPage(venueId, categoryId)
    }

    private fun newPage(venueId: UUID, categoryId: UUID, principal: PasslyUserPrincipal, model: Model): String {
        val category = categories.getTree(VenueId(venueId))[CategoryId(categoryId)]
        model.addAttribute("formTitle", "New item in ${category.name}")
        model.addAttribute("formAction", "/admin/venues/$venueId/categories/$categoryId/items")
        return formPage(venueId, category.id.value, category.name, principal, model)
    }

    private fun editPage(venueId: UUID, id: UUID, principal: PasslyUserPrincipal, model: Model): String {
        val venue = VenueId(venueId)
        val item = items.get(venue, MenuItemId(id))
        val tree = categories.getTree(venue)
        val category = tree[checkNotNull(item.categoryId)]
        model.addAttribute("formTitle", "Edit ${item.name}")
        model.addAttribute("formAction", "/admin/venues/$venueId/items/$id")
        model.addAttribute("moveAction", "/admin/venues/$venueId/items/$id/move")
        model.addAttribute("categoryOptions", tree.categoryOptions())
        model.addAttribute("currentCategoryId", category.id.value)
        return formPage(venueId, category.id.value, category.name, principal, model)
    }

    private fun formPage(
        venueId: UUID,
        categoryId: UUID,
        categoryName: String,
        principal: PasslyUserPrincipal,
        model: Model,
    ): String {
        val venue = VenueId(venueId)
        model.addAttribute("venue", venues.find(principal, venueId))
        model.addAttribute("category", CategoryRefView(categoryId, categoryName))
        model.addAttribute("stations", stations.list(venue).map { it.toOption() })
        model.addAttribute("modifierGroups", modifierGroups.list(venue).map { it.toOption() })
        return "admin/item-form"
    }

    private fun listPage(venueId: UUID, categoryId: UUID) =
        "redirect:/admin/venues/$venueId/categories/$categoryId/items"
}
