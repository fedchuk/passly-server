package com.passly.web.api.menu

import com.passly.application.menu.MoveMenuItemCommand
import com.passly.application.menu.ReorderMenuItemsCommand
import com.passly.domain.common.Money
import com.passly.domain.common.TaxRate
import com.passly.domain.menu.CategoryId
import com.passly.domain.menu.MenuItemDetails
import com.passly.domain.menu.MenuItemId
import com.passly.domain.menu.StationId
import com.passly.domain.venue.VenueId
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.util.UUID

private const val MONEY = """^\d{1,8}(\.\d{1,2})?$"""
private const val PERCENT = """^\d{1,3}(\.\d{1,2})?$"""

data class CreateMenuItemRequest(
    @NotBlank @Size(max = 160) val name: String,
    @Size(max = 2000) val description: String? = null,
    @Pattern(regexp = MONEY) val price: String,
    @Pattern(regexp = PERCENT) val taxRate: String,
    val stationId: UUID? = null,
) {
    fun toDetails() = menuItemDetails(name, description, price, taxRate, stationId)
}

data class UpdateMenuItemRequest(
    @NotBlank @Size(max = 160) val name: String,
    @Size(max = 2000) val description: String? = null,
    @Pattern(regexp = MONEY) val price: String,
    @Pattern(regexp = PERCENT) val taxRate: String,
    val stationId: UUID? = null,
    val version: Long,
) {
    fun toDetails() = menuItemDetails(name, description, price, taxRate, stationId)
}

data class AvailabilityRequest(
    val available: Boolean,
)

data class MoveMenuItemRequest(
    val categoryId: UUID,
) {
    fun toCommand(venueId: VenueId, id: MenuItemId) = MoveMenuItemCommand(venueId, id, CategoryId(categoryId))
}

data class ReorderMenuItemsRequest(
    @NotEmpty val orderedIds: List<UUID>,
) {
    fun toCommand(venueId: VenueId, categoryId: CategoryId) =
        ReorderMenuItemsCommand(venueId, categoryId, orderedIds.map(::MenuItemId))
}

private fun menuItemDetails(name: String, description: String?, price: String, taxRate: String, stationId: UUID?) =
    MenuItemDetails(
        name = name,
        description = description,
        price = Money.of(price),
        taxRate = TaxRate.of(taxRate),
        stationId = stationId?.let(::StationId),
    )
