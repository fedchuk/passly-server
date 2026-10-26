package com.passly.application.tax

import com.passly.domain.common.TaxRate
import com.passly.domain.tax.VenueTaxRateId
import com.passly.domain.venue.VenueId

data class CreateTaxRateCommand(
    val venueId: VenueId,
    val name: String,
    val rate: TaxRate,
)

data class ChangeTaxRateCommand(
    val venueId: VenueId,
    val taxRateId: VenueTaxRateId,
    val name: String,
    val rate: TaxRate,
    val expectedVersion: Long? = null,
)
