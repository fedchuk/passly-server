package com.passly.web.admin

import com.passly.application.access.AccessService
import com.passly.application.menu.CategoryService
import com.passly.domain.common.NotFoundException
import com.passly.domain.user.UserId
import com.passly.domain.venue.VenueId
import com.passly.security.PasslyUserPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import java.util.UUID

@Controller
@RequestMapping("/admin/venues/{venueId}")
class VenueAdminController(
    private val access: AccessService,
    private val categories: CategoryService,
) {

    @GetMapping
    fun venue(
        @PathVariable venueId: UUID,
        @AuthenticationPrincipal principal: PasslyUserPrincipal,
        model: Model,
    ): String {
        val venue = access.venuesOf(UserId(principal.userId)).firstOrNull { it.venueId.value == venueId }
            ?: throw NotFoundException("Venue", venueId)
        model.addAttribute("venue", venue.toView())
        model.addAttribute("tree", categories.getTree(VenueId(venueId)).toNodeViews())
        return "admin/venue"
    }
}