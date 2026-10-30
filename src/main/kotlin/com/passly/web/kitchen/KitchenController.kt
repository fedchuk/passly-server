package com.passly.web.kitchen

import com.passly.application.kitchen.KitchenService
import com.passly.application.kitchen.MoveLinesCommand
import com.passly.domain.common.NotFoundException
import com.passly.domain.common.ValidationException
import com.passly.domain.menu.StationId
import com.passly.domain.order.OrderId
import com.passly.domain.order.OrderLineId
import com.passly.domain.order.PrepStatus
import com.passly.domain.venue.VenueId
import com.passly.security.PasslyUserPrincipal
import com.passly.web.admin.AdminVenues
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.time.Clock
import java.util.*

@Controller
@RequestMapping("/kitchen/venues/{venueId}")
class KitchenController(
    private val venues: AdminVenues,
    private val kitchen: KitchenService,
    private val clock: Clock,
) {

    @GetMapping
    fun board(
        @PathVariable venueId: UUID,
        @AuthenticationPrincipal principal: PasslyUserPrincipal,
        model: Model,
    ): String {
        model.addAttribute("venue", venues.find(principal, venueId))
        model.addAttribute("stations", kitchen.stations(VenueId(venueId)).toTabs(null))
        return "kitchen/board"
    }

    @GetMapping("/stations/{stationId}")
    fun station(
        @PathVariable venueId: UUID,
        @PathVariable stationId: UUID,
        @AuthenticationPrincipal principal: PasslyUserPrincipal,
        model: Model,
    ): String {
        val station = kitchen.station(VenueId(venueId), StationId(stationId))
        model.addAttribute("venue", venues.find(principal, venueId))
        model.addAttribute("station", station.name)
        model.addAttribute("stationId", stationId)
        model.addAttribute("stations", kitchen.stations(VenueId(venueId)).toTabs(stationId))
        model.addAttribute("board", kitchen.tickets(VenueId(venueId), StationId(stationId)).toBoard(clock.instant()))
        model.addAttribute("refreshSeconds", REFRESH_SECONDS)
        return "kitchen/station"
    }

    @PostMapping("/stations/{stationId}/lines")
    fun move(
        @PathVariable venueId: UUID,
        @PathVariable stationId: UUID,
        @RequestParam orderId: UUID,
        @RequestParam("lineId") lineIds: List<UUID>,
        @RequestParam status: PrepStatus,
        flash: RedirectAttributes,
    ): String {
        try {
            kitchen.moveLines(MoveLinesCommand(VenueId(venueId), OrderId(orderId), lineIds.map(::OrderLineId), status))
        } catch (e: NotFoundException) {
            flash.addFlashAttribute("error", e.message)
        } catch (e: ValidationException) {
            flash.addFlashAttribute("error", e.message)
        }
        return "redirect:/kitchen/venues/$venueId/stations/$stationId"
    }

    private companion object {
        const val REFRESH_SECONDS = 15
    }
}
