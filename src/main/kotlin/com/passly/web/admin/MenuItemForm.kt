package com.passly.web.admin

import com.passly.domain.common.Money
import com.passly.domain.menu.MenuItem
import com.passly.domain.menu.MenuItemDetails
import com.passly.domain.menu.ModifierGroupId
import com.passly.domain.menu.StationId
import com.passly.domain.tax.VenueTaxRateId
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.util.*

class MenuItemForm {
    @NotBlank(message = "Name must not be blank")
    @Size(max = 160, message = "Name is too long (max 160)")
    var name: String = ""

    @Size(max = 2000, message = "Description is too long (max 2000)")
    var description: String = ""

    @Pattern(regexp = MONEY, message = "Enter a price like 4.50")
    var price: String = ""

    var stationId: UUID? = null

    var taxRateId: UUID? = null

    var modifierGroupIds: MutableList<UUID> = mutableListOf()

    var version: Long? = null

    fun details() = MenuItemDetails(
        name = name,
        description = description.ifBlank { null },
        price = Money.of(price.withDecimalPoint()),
        stationId = stationId?.let(::StationId),
        taxRateId = taxRateId?.let(::VenueTaxRateId),
    )

    fun groupIds() = modifierGroupIds.map(::ModifierGroupId)

    private fun String.withDecimalPoint() = trim().replace(',', '.')

    companion object {
        const val MONEY = "^\\s*\\d{1,8}([.,]\\d{1,2})?\\s*$"

        fun of(item: MenuItem) = MenuItemForm().apply {
            name = item.name
            description = item.description.orEmpty()
            price = item.price.toString()
            stationId = item.stationId?.value
            taxRateId = item.taxRateId?.value
            modifierGroupIds = item.modifierGroupIds.map { it.value }.toMutableList()
            version = item.version
        }
    }
}
