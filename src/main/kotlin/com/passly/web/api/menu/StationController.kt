package com.passly.web.api.menu

import com.passly.application.menu.StationService
import com.passly.domain.venue.VenueId
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@RequestMapping("/api/v1/venues/{venueId}/stations")
@Tag(name = "Stations", description = "Where orders are prepared, such as the kitchen or the bar")
class StationController(private val stations: StationService) {

    @GetMapping
    fun list(@PathVariable venueId: UUID): List<StationResponse> =
        stations.list(VenueId(venueId)).map { it.toResponse() }
}
