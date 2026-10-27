package com.passly.web.api.menu

import com.passly.application.menu.AttachModifierGroupsCommand
import com.passly.application.menu.MoveMenuItemCommand
import com.passly.application.menu.ReorderMenuItemsCommand
import com.passly.domain.common.Money
import com.passly.domain.menu.*
import com.passly.domain.tax.VenueTaxRateId
import com.passly.domain.venue.VenueId
import com.passly.web.api.VERSION_DESCRIPTION
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.util.*

private const val MONEY = """^\d{1,8}(\.\d{1,2})?$"""
private const val TAX_RATE_DESCRIPTION = "VAT rate from GET …/tax-rates. Leave empty to use the rate of the category"

data class CreateMenuItemRequest(
    @NotBlank @Size(max = 160) val name: String,
    @Size(max = 2000) val description: String? = null,
    @Schema(description = "Price as a string with up to 2 decimals", example = "4.50")
    @Pattern(regexp = MONEY) val price: String,
    @Schema(description = "Leave empty to use the station of the category")
    val stationId: UUID? = null,
    @Schema(description = TAX_RATE_DESCRIPTION)
    val taxRateId: UUID? = null,
) {
    fun toDetails() = menuItemDetails(name, description, price, stationId, taxRateId)
}

data class UpdateMenuItemRequest(
    @NotBlank @Size(max = 160) val name: String,
    @Size(max = 2000) val description: String? = null,
    @Schema(description = "Price as a string with up to 2 decimals", example = "4.50")
    @Pattern(regexp = MONEY) val price: String,
    @Schema(description = "Leave empty to use the station of the category")
    val stationId: UUID? = null,
    @Schema(description = TAX_RATE_DESCRIPTION)
    val taxRateId: UUID? = null,
    @Schema(description = VERSION_DESCRIPTION, example = "0")
    val version: Long,
) {
    fun toDetails() = menuItemDetails(name, description, price, stationId, taxRateId)
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

data class AttachModifierGroupsRequest(
    @Schema(description = "All groups of the item in display order. An empty list detaches them all.")
    val groupIds: List<UUID>,
    @Schema(description = VERSION_DESCRIPTION, example = "0")
    val version: Long,
) {
    fun toCommand(venueId: VenueId, id: MenuItemId) =
        AttachModifierGroupsCommand(venueId, id, groupIds.map(::ModifierGroupId), version)
}

private fun menuItemDetails(name: String, description: String?, price: String, stationId: UUID?, taxRateId: UUID?) =
    MenuItemDetails(
        name = name,
        description = description,
        price = Money.of(price),
        stationId = stationId?.let(::StationId),
        taxRateId = taxRateId?.let(::VenueTaxRateId),
    )
