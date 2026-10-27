package com.passly.web.api.menu

import com.passly.application.menu.CreateCategoryCommand
import com.passly.application.menu.MoveCategoryCommand
import com.passly.application.menu.ReorderCategoriesCommand
import com.passly.application.menu.UpdateCategoryCommand
import com.passly.domain.menu.CategoryId
import com.passly.domain.menu.StationId
import com.passly.domain.tax.VenueTaxRateId
import com.passly.domain.venue.VenueId
import com.passly.web.api.VERSION_DESCRIPTION
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.util.*

private const val STATION_DESCRIPTION =
    "Where orders for this category are prepared. Required for a top-level category. " +
            "A subcategory without it uses the station of the nearest parent that has one."

private const val TAX_RATE_DESCRIPTION =
    "VAT rate from GET …/tax-rates. A subcategory without it uses the rate of the nearest parent that has one."

data class CreateCategoryRequest(
    @NotBlank @Size(max = 120) val name: String,
    val parentId: UUID? = null,
    @Schema(description = STATION_DESCRIPTION)
    val stationId: UUID? = null,
    @Schema(description = "$TAX_RATE_DESCRIPTION Empty for a top-level category means the first rate of the venue.")
    val taxRateId: UUID? = null,
) {
    fun toCommand(venueId: VenueId) =
        CreateCategoryCommand(
            venueId,
            name,
            parentId?.let(::CategoryId),
            stationId?.let(::StationId),
            taxRateId?.let(::VenueTaxRateId),
        )
}

data class UpdateCategoryRequest(
    @NotBlank @Size(max = 120) val name: String,
    @Schema(description = STATION_DESCRIPTION)
    val stationId: UUID? = null,
    @Schema(description = "Inactive categories are hidden in the waiter app")
    val active: Boolean,
    @Schema(description = VERSION_DESCRIPTION, example = "0")
    val version: Long,
    @Schema(description = "$TAX_RATE_DESCRIPTION Empty for a top-level category keeps its current rate.")
    val taxRateId: UUID? = null,
) {
    fun toCommand(venueId: VenueId, id: CategoryId) =
        UpdateCategoryCommand(venueId, id, name, stationId?.let(::StationId), active, version, taxRateId?.let(::VenueTaxRateId))
}

data class MoveCategoryRequest(
    @Schema(description = "Leave empty to move to the top level")
    val newParentId: UUID? = null,
    @Schema(description = "Position among the new siblings, starting at 0. Leave empty to put it last.")
    @PositiveOrZero val position: Int? = null,
) {
    fun toCommand(venueId: VenueId, id: CategoryId) =
        MoveCategoryCommand(venueId, id, newParentId?.let(::CategoryId), position)
}

data class ReorderCategoriesRequest(
    val parentId: UUID? = null,
    @NotEmpty val orderedIds: List<UUID>,
) {
    fun toCommand(venueId: VenueId) =
        ReorderCategoriesCommand(venueId, parentId?.let(::CategoryId), orderedIds.map(::CategoryId))
}
