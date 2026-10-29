package com.passly.web.admin

import com.passly.domain.common.ValidationException
import com.passly.domain.discount.DiscountPercent
import com.passly.domain.discount.VenueDiscount
import java.util.*

data class DiscountRowView(
    val id: UUID,
    val name: String,
    val percent: String,
    val version: Long?,
)

fun VenueDiscount.toRowView() = DiscountRowView(id.value, name, percent.value.stripTrailingZeros().toPlainString(), version)

fun parseDiscountPercent(text: String): DiscountPercent {
    val percent = text.trim().removeSuffix("%").trim().replace(',', '.').toBigDecimalOrNull()
        ?: throw ValidationException("Discount '$text' is not a number", "percent")
    return DiscountPercent.of(percent)
}
