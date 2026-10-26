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
        val focus = focusId ?: tree.children(null).firstOrNull()?.id
        val openRootId = focus?.let { tree.pathTo(it).first().id }
        val venueStations = stations.list(venue)
        val itemCounts = items.countByCategory(venue)

        model.addAttribute("venue", venues.find(principal, venueId))
        model.addAttribute("stations", venueStations.map { it.toOption() })
        model.addAttribute("roots", tree.toRootViews(itemCounts, venueStations, openRootId))
        model.addAttribute(
            "focus",
            focus?.let { tree.toFocusView(it, items.listInCategory(venue, it), itemCounts, venueStations) },
        )
        return "admin/menu"
    }
}
