package com.passly.web.menu

import com.passly.application.menu.CreateCategoryCommand
import com.passly.application.menu.MoveCategoryCommand
import com.passly.application.menu.ReorderCategoriesCommand
import com.passly.application.menu.UpdateCategoryCommand
import com.passly.domain.menu.CategoryId
import com.passly.domain.menu.StationId
import com.passly.domain.venue.VenueId
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
    val active: Boolean,
    val version: Long,
) {
    fun toCommand(venueId: VenueId, id: CategoryId) =
        UpdateCategoryCommand(venueId, id, name, stationId?.let(::StationId), active, version)
}

data class MoveCategoryRequest(
    val newParentId: UUID? = null,
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
