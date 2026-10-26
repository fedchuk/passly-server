package com.passly.web.admin

import com.passly.application.menu.CategoryService
import com.passly.application.menu.MenuItemService
import com.passly.application.menu.StationService
import com.passly.domain.menu.CategoryId
import com.passly.domain.venue.VenueId
import com.passly.security.PasslyUserPrincipal
import org.springframework.stereotype.Component
import org.springframework.ui.Model
import java.util.*

@Component
class MenuPages(
    private val venues: AdminVenues,
    private val categories: CategoryService,
    private val items: MenuItemService,
    private val stations: StationService,
) {

    fun show(principal: PasslyUserPrincipal, venueId: UUID, focusId: CategoryId?, model: Model): String {
        val venue = VenueId(venueId)
        val tree = categories.getTree(venue)
        val openPath = focusId?.let { tree.pathTo(it).map { category -> category.id }.toSet() }.orEmpty()
        val venueStations = stations.list(venue)

        model.addAttribute("venue", venues.find(principal, venueId))
        model.addAttribute("stations", venueStations.map { it.toOption() })
        model.addAttribute(
            "roots",
            tree.toNodeViews(openPath, items.countByCategory(venue), venueStations) { items.listInCategory(venue, it) },
        )
        return "admin/menu"
    }
}
