package com.passly.web.service

import com.passly.application.order.FloorStatusQuery
import com.passly.domain.floor.DiningTableId
import com.passly.domain.venue.VenueId
import com.passly.security.PasslyUserPrincipal
import com.passly.web.admin.AdminVenues
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import java.time.Clock
import java.util.*

@Controller
@RequestMapping("/service/venues/{venueId}")
class ServiceController(
    private val venues: AdminVenues,
    private val floorStatus: FloorStatusQuery,
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
        model.addAttribute("table", floorStatus.tableOf(VenueId(venueId), DiningTableId(tableId)).toPageView(clock.instant()))
        return "service/table"
    }
}
