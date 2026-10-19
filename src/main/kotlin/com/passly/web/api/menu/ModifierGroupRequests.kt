package com.passly.web.api.menu

import com.passly.application.menu.CreateModifierGroupCommand
import com.passly.application.menu.UpdateModifierGroupCommand
import com.passly.domain.common.Money
import com.passly.domain.menu.ModifierDraft
import com.passly.domain.menu.ModifierGroupId
import com.passly.domain.menu.ModifierId
import com.passly.domain.venue.VenueId
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.util.UUID

private const val SIGNED_MONEY = """^-?\d{1,8}(\.\d{1,2})?$"""

data class ModifierRequest(
    val id: UUID? = null,
    @NotBlank @Size(max = 120) val name: String,
    @Pattern(regexp = SIGNED_MONEY) val priceDelta: String = "0",
) {
    fun toDraft() = ModifierDraft(name, Money.of(priceDelta), id?.let(::ModifierId))
}

data class CreateModifierGroupRequest(
    @NotBlank @Size(max = 120) val name: String,
    @PositiveOrZero val minSelect: Int,
    @Positive val maxSelect: Int,
    @NotEmpty @Valid val options: List<ModifierRequest>,
) {
    fun toCommand(venueId: VenueId) =
        CreateModifierGroupCommand(venueId, name, minSelect, maxSelect, options.map { it.toDraft() })
}

data class UpdateModifierGroupRequest(
    @NotBlank @Size(max = 120) val name: String,
    @PositiveOrZero val minSelect: Int,
    @Positive val maxSelect: Int,
    @NotEmpty @Valid val options: List<ModifierRequest>,
    val version: Long,
) {
    fun toCommand(venueId: VenueId, id: ModifierGroupId) =
        UpdateModifierGroupCommand(venueId, id, name, minSelect, maxSelect, options.map { it.toDraft() }, version)
}
