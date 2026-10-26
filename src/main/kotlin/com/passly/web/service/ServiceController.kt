package com.passly.web.service

import com.passly.application.menu.WaiterMenuService
import com.passly.application.order.AddRoundCommand
import com.passly.application.order.FloorStatusQuery
import com.passly.application.order.OpenOrderCommand
import com.passly.application.order.OrderService
import com.passly.domain.common.ConflictException
import com.passly.domain.common.UnavailableItemsException
import com.passly.domain.common.ValidationException
import com.passly.domain.floor.DiningTableId
import com.passly.domain.order.OrderId
import com.passly.domain.user.UserId
import com.passly.domain.venue.VenueId
import com.passly.security.PasslyUserPrincipal
import com.passly.web.admin.AdminVenues
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.time.Clock
import java.util.*

@Controller
@RequestMapping("/service/venues/{venueId}")
class ServiceController(
    private val venues: AdminVenues,
    private val floorStatus: FloorStatusQuery,
    private val menus: WaiterMenuService,
    private val orders: OrderService,
    private val clock: Clock,
) {

    @GetMapping
    fun floor(
        @PathVariable venueId: UUID,
        @AuthenticationPrincipal principal: PasslyUserPrincipal,
        model: Model,
    ): String {
        model.addAttribute("venue", venues.find(principal, venueId))
        val areas = floorStatus.floorOf(VenueId(venueId)).toAreaViews(clock.instant())
        model.addAttribute("areas", areas)
        model.addAttribute("summary", areas.summary())
        return "service/floor"
    }

    @GetMapping("/tables/{tableId}")
    fun table(
        @PathVariable venueId: UUID,
        @PathVariable tableId: UUID,
        @AuthenticationPrincipal principal: PasslyUserPrincipal,
        model: Model,
    ): String {
        model.addAttribute("venue", venues.find(principal, venueId))
        val status = floorStatus.tableOf(VenueId(venueId), DiningTableId(tableId))
        model.addAttribute("table", status.toPageView(clock.instant()))
        model.addAttribute("orderId", status.order?.id?.value)
        val menu = menus.menu(VenueId(venueId))
        model.addAttribute("sections", menu.toSectionViews())
        model.addAttribute("modifierGroups", menu.toModifierGroupViews())
        return "service/table"
    }

    @PostMapping("/tables/{tableId}/rounds")
    fun send(
        @PathVariable venueId: UUID,
        @PathVariable tableId: UUID,
        @ModelAttribute form: RoundForm,
        binding: BindingResult,
        @AuthenticationPrincipal principal: PasslyUserPrincipal,
        flash: RedirectAttributes,
    ): String {
        val tablePage = "redirect:/service/venues/$venueId/tables/$tableId"
        if (binding.hasErrors()) {
            flash.addFlashAttribute("error", "The round could not be read. Reload the page and try again.")
            return tablePage
        }
        val waiterId = UserId(principal.userId)
        val orderId = form.orderId
        try {
            if (orderId == null) {
                orders.open(
                    OpenOrderCommand(
                        VenueId(venueId),
                        DiningTableId(tableId),
                        waiterId,
                        form.guests,
                        form.requests()
                    )
                )
                flash.addFlashAttribute("message", "Table opened")
            } else {
                val order = orders.addRound(
                    AddRoundCommand(
                        VenueId(venueId),
                        OrderId(orderId),
                        waiterId,
                        form.requests()
                    )
                )
                flash.addFlashAttribute("message", "Round ${order.rounds} sent")
            }
            flash.addFlashAttribute("sent", true)
        } catch (e: UnavailableItemsException) {
            flash.addFlashAttribute("error", e.message)
            flash.addFlashAttribute("unavailable", e.itemIds.joinToString(","))
        } catch (e: ValidationException) {
            flash.addFlashAttribute("error", e.message)
        } catch (e: ConflictException) {
            flash.addFlashAttribute("error", e.message)
        }
        return tablePage
    }
}
