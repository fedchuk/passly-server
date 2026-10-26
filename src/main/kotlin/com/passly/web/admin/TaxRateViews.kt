package com.passly.web.admin

import com.passly.domain.common.TaxRate
import com.passly.domain.common.ValidationException
import com.passly.domain.tax.TaxRatePreset
import com.passly.domain.tax.VenueTaxRate
import java.util.*

data class TaxRateRowView(
    val id: UUID,
    val name: String,
    val rate: String,
    val version: Long?,
)

fun VenueTaxRate.toRowView() = TaxRateRowView(id.value, name, rate.percent.stripTrailingZeros().toPlainString(), version)

fun List<TaxRatePreset>.toPresetLabel(): String =
    joinToString(", ") { "${it.name} ${it.rate.percent.stripTrailingZeros().toPlainString()}%" }

fun parseTaxRate(text: String): TaxRate {
    val percent = text.trim().removeSuffix("%").trim().replace(',', '.').toBigDecimalOrNull()
        ?: throw ValidationException("Tax rate '$text' is not a number", "rate")
    return TaxRate.of(percent)
}
