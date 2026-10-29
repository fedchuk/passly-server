package com.passly.application.discount

import com.passly.domain.discount.DiscountPercent
import com.passly.domain.discount.VenueDiscountId
import com.passly.domain.venue.VenueId

data class CreateDiscountCommand(
    val venueId: VenueId,
    val name: String,
    val percent: DiscountPercent,
)

data class ChangeDiscountCommand(
    val venueId: VenueId,
    val discountId: VenueDiscountId,
    val name: String,
    val percent: DiscountPercent,
    val expectedVersion: Long? = null,
)
