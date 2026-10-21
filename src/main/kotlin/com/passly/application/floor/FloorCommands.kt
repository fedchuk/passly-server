package com.passly.application.floor

import com.passly.domain.floor.DiningAreaId
import com.passly.domain.floor.DiningTableId
import com.passly.domain.venue.VenueId

data class CreateAreaCommand(
    val venueId: VenueId,
    val name: String,
)

data class RenameAreaCommand(
    val venueId: VenueId,
    val areaId: DiningAreaId,
    val name: String,
    val expectedVersion: Long? = null,
)

data class CreateTableCommand(
    val venueId: VenueId,
    val areaId: DiningAreaId,
    val label: String,
    val seats: Int,
)

data class CreateTablesCommand(
    val venueId: VenueId,
    val areaId: DiningAreaId,
    val numbers: IntRange,
    val labelPrefix: String = "",
    val seats: Int,
)

data class UpdateTableCommand(
    val venueId: VenueId,
    val tableId: DiningTableId,
    val areaId: DiningAreaId,
    val label: String,
    val seats: Int,
    val active: Boolean,
    val expectedVersion: Long? = null,
)
