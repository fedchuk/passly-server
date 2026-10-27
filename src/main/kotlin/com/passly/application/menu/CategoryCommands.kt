package com.passly.application.menu

import com.passly.domain.menu.CategoryId
import com.passly.domain.menu.StationId
import com.passly.domain.tax.VenueTaxRateId
import com.passly.domain.venue.VenueId

data class CreateCategoryCommand(
    val venueId: VenueId,
    val name: String,
    val parentId: CategoryId? = null,
    val stationId: StationId? = null,
    val taxRateId: VenueTaxRateId? = null,
)

data class UpdateCategoryCommand(
    val venueId: VenueId,
    val categoryId: CategoryId,
    val name: String,
    val stationId: StationId?,
    val active: Boolean,
    val expectedVersion: Long? = null,
    val taxRateId: VenueTaxRateId? = null,
)

data class MoveCategoryCommand(
    val venueId: VenueId,
    val categoryId: CategoryId,
    val newParentId: CategoryId?,
    val position: Int? = null,
)

data class ReorderCategoriesCommand(
    val venueId: VenueId,
    val parentId: CategoryId?,
    val orderedIds: List<CategoryId>,
)
