package com.passly.domain.order

import com.passly.domain.discount.DiscountPercent
import com.passly.domain.discount.VenueDiscountId
import com.passly.domain.user.UserId
import java.time.Instant

data class OrderDiscount(
    val discountId: VenueDiscountId,
    val name: String,
    val percent: DiscountPercent,
    val appliedBy: UserId,
    val appliedAt: Instant,
)
