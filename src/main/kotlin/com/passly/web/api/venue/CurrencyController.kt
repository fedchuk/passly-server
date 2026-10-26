package com.passly.web.api.venue

import com.passly.application.venue.VenueSettingsService
import com.passly.domain.venue.VenueId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@RequestMapping("/api/v1/venues/{venueId}/currencies")
@Tag(name = "Currencies", description = "What guests can pay in")
class CurrencyController(private val settings: VenueSettingsService) {

    @GetMapping
    @Operation(
        summary = "Main and accepted currencies",
        description = "Rates are set by the manager in the back office. Use them to show the due in another currency; " +
                "the server converts the payment with the rate it has at that moment.",
    )
    fun currencies(@PathVariable venueId: UUID): CurrenciesResponse =
        settings.get(VenueId(venueId)).toCurrenciesResponse()
}
