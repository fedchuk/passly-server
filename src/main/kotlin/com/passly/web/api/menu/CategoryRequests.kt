package com.passly.web.api.menu

import com.passly.application.menu.CreateCategoryCommand
import com.passly.application.menu.MoveCategoryCommand
import com.passly.application.menu.ReorderCategoriesCommand
import com.passly.application.menu.UpdateCategoryCommand
import com.passly.domain.menu.CategoryId
import com.passly.domain.menu.StationId
import com.passly.domain.venue.VenueId
import com.passly.web.api.VERSION_DESCRIPTION
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.util.UUID

data class CreateCategoryRequest(
    @NotBlank @Size(max = 120) val name: String,
    val parentId: UUID? = null,
    val stationId: UUID? = null,
) {
    fun toCommand(venueId: VenueId) =
        CreateCategoryCommand(venueId, name, parentId?.let(::CategoryId), stationId?.let(::StationId))
}

data class UpdateCategoryRequest(
    @NotBlank @Size(max = 120) val name: String,
    val stationId: UUID? = null,
    @Schema(description = "Inactive categories are hidden in the waiter app")
    val active: Boolean,
    @Schema(description = VERSION_DESCRIPTION, example = "0")
    val version: Long,
) {
    fun toCommand(venueId: VenueId, id: CategoryId) =
        UpdateCategoryCommand(venueId, id, name, stationId?.let(::StationId), active, version)
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
