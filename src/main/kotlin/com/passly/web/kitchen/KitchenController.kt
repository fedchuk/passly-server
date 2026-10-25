package com.passly.web.kitchen

import com.passly.security.PasslyUserPrincipal
import com.passly.web.admin.AdminVenues
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import java.util.*

@Controller
@RequestMapping("/kitchen/venues/{venueId}")
class KitchenController(private val venues: AdminVenues) {

    @GetMapping
    fun board(
        @PathVariable venueId: UUID,
        @AuthenticationPrincipal principal: PasslyUserPrincipal,
        model: Model,
    ): String {
        model.addAttribute("venue", venues.find(principal, venueId))
        return "kitchen/board"
    }
}
