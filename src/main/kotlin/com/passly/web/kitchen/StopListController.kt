package com.passly.web.kitchen

import com.passly.application.menu.MenuItemService
import com.passly.application.menu.WaiterMenuService
import com.passly.domain.menu.MenuItemId
import com.passly.domain.venue.VenueId
import com.passly.security.PasslyUserPrincipal
import com.passly.web.admin.AdminVenues
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.util.*

@Controller
@RequestMapping("/kitchen/venues/{venueId}/stop-list")
class StopListController(
    private val venues: AdminVenues,
    private val menus: WaiterMenuService,
    private val items: MenuItemService,
) {

    @GetMapping
    fun list(
        @PathVariable venueId: UUID,
        @AuthenticationPrincipal principal: PasslyUserPrincipal,
        model: Model,
    ): String {
        model.addAttribute("venue", venues.find(principal, venueId))
        val all = menus.menu(VenueId(venueId)).toStopListItems()
        val stopped = all.filterNot { it.available }
        model.addAttribute("stopped", stopped)
        model.addAttribute("available", all.filter { it.available })
        model.addAttribute("summary", stopListSummary(stopped.size))
        return "kitchen/stop-list"
    }

    @PostMapping("/{itemId}")
    fun change(
        @PathVariable venueId: UUID,
        @PathVariable itemId: UUID,
        @RequestParam available: Boolean,
        flash: RedirectAttributes,
    ): String {
        val item = items.changeAvailability(VenueId(venueId), MenuItemId(itemId), available)
        val state = if (available) "is back on the menu" else "is on the stop list"
        flash.addFlashAttribute("message", "'${item.name}' $state")
        return "redirect:/kitchen/venues/$venueId/stop-list"
    }
}
