package com.passly.web.api.venue

import com.passly.application.discount.DiscountService
import com.passly.domain.venue.VenueId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@RequestMapping("/api/v1/venues/{venueId}/discounts")
@Tag(name = "Discounts", description = "Ready discounts for the whole bill")
class DiscountController(private val discounts: DiscountService) {

    @GetMapping
    @Operation(
        summary = "Discounts in display order",
        description = "Managed in the back office settings. Apply one with PUT …/orders/{id}/discount.",
    )
    fun list(@PathVariable venueId: UUID): List<DiscountResponse> =
        discounts.list(VenueId(venueId)).map { it.toResponse() }
}
