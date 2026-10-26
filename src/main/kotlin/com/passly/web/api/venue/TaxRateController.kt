package com.passly.web.api.venue

import com.passly.application.tax.TaxRateService
import com.passly.domain.venue.VenueId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@RequestMapping("/api/v1/venues/{venueId}/tax-rates")
@Tag(name = "Tax rates", description = "VAT rates of the venue")
class TaxRateController(private val taxRates: TaxRateService) {

    @GetMapping
    @Operation(summary = "Tax rates in display order", description = "Managed in the back office settings.")
    fun list(@PathVariable venueId: UUID): List<TaxRateResponse> =
        taxRates.list(VenueId(venueId)).map { it.toResponse() }
}
