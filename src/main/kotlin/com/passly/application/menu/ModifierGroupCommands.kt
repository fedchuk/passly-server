package com.passly.application.menu

import com.passly.domain.menu.ModifierDraft
import com.passly.domain.menu.ModifierGroupId
import com.passly.domain.venue.VenueId

data class CreateModifierGroupCommand(
    val venueId: VenueId,
    val name: String,
    val minSelect: Int,
    val maxSelect: Int,
    val options: List<ModifierDraft>,
)

data class UpdateModifierGroupCommand(
    val venueId: VenueId,
    val groupId: ModifierGroupId,
    val name: String,
    val minSelect: Int,
    val maxSelect: Int,
    val options: List<ModifierDraft>,
    val expectedVersion: Long? = null,
)
