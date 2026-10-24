package com.passly.web.admin

import com.passly.application.menu.CategoryService
import com.passly.application.menu.StationService
import com.passly.application.venue.VenueOverviewQuery
import com.passly.domain.venue.VenueId
import com.passly.security.PasslyUserPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import java.util.*

@Controller
@RequestMapping("/admin/venues/{venueId}")
class VenueAdminController(
    private val venues: AdminVenues,
    private val categories: CategoryService,
    private val stations: StationService,
    private val overviews: VenueOverviewQuery,
) {

    @GetMapping
    fun menu(
        @PathVariable venueId: UUID,
        @AuthenticationPrincipal principal: PasslyUserPrincipal,
        model: Model,
    ): String {
        val venueStations = stations.list(VenueId(venueId))
        model.addAttribute("venue", venues.find(principal, venueId))
        model.addAttribute("tree", categories.getTree(VenueId(venueId)).toNodeViews(venueStations.associate { it.id to it.name }))
        model.addAttribute("stations", venueStations.map { it.toOption() })
        return "admin/venue"
    }

    @GetMapping("/overview")
    fun overview(
        @PathVariable venueId: UUID,
        @AuthenticationPrincipal principal: PasslyUserPrincipal,
        model: Model,
    ): String {
        model.addAttribute("venue", venues.find(principal, venueId))
        model.addAttribute("overview", overviews.overviewOf(VenueId(venueId)))
        return "admin/overview"
    }
}
