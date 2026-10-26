package com.passly.web.api.venue

import com.passly.domain.tax.VenueTaxRate
import io.swagger.v3.oas.annotations.media.Schema
import java.util.*

data class TaxRateResponse(
    val id: UUID,
    @Schema(example = "Alcohol")
    val name: String,
    @Schema(description = "Percent of VAT included in the price", example = "20.00")
    val rate: String,
)

fun VenueTaxRate.toResponse() = TaxRateResponse(id.value, name, rate.percent.toPlainString())
