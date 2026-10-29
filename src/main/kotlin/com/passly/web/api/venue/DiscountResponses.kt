package com.passly.web.api.venue

import com.passly.domain.discount.VenueDiscount
import io.swagger.v3.oas.annotations.media.Schema
import java.util.*

data class DiscountResponse(
    val id: UUID,
    @Schema(example = "Regular guest")
    val name: String,
    @Schema(description = "Percent taken off the whole bill", example = "10.00")
    val percent: String,
)

fun VenueDiscount.toResponse() = DiscountResponse(id.value, name, percent.value.toPlainString())
