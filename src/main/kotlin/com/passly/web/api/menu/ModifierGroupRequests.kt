package com.passly.web.api.menu

import com.passly.application.menu.CreateModifierGroupCommand
import com.passly.application.menu.UpdateModifierGroupCommand
import com.passly.domain.common.Money
import com.passly.domain.menu.ModifierDraft
import com.passly.domain.menu.ModifierGroupId
import com.passly.domain.menu.ModifierId
import com.passly.domain.venue.VenueId
import com.passly.web.api.VERSION_DESCRIPTION
import io.swagger.v3.oas.annotations.media.Schema
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
    @Schema(description = "Id of an existing option. Leave empty for a new one.")
    val id: UUID? = null,
    @NotBlank @Size(max = 120) val name: String,
    @Schema(description = "Added to the item price, can be negative", example = "-0.50")
    @Pattern(regexp = SIGNED_MONEY) val priceDelta: String = "0",
) {
    fun toDraft() = ModifierDraft(name, Money.of(priceDelta), id?.let(::ModifierId))
}

data class CreateModifierGroupRequest(
    @NotBlank @Size(max = 120) val name: String,
    @Schema(description = "How many options the guest must choose at least. 0 makes the group optional.", example = "1")
    @PositiveOrZero val minSelect: Int,
    @Schema(description = "How many options the guest can choose at most", example = "1")
    @Positive val maxSelect: Int,
    @NotEmpty @Valid val options: List<ModifierRequest>,
) {
    fun toCommand(venueId: VenueId) =
        CreateModifierGroupCommand(venueId, name, minSelect, maxSelect, options.map { it.toDraft() })
}

data class UpdateModifierGroupRequest(
    @NotBlank @Size(max = 120) val name: String,
    @Schema(description = "How many options the guest must choose at least. 0 makes the group optional.", example = "1")
    @PositiveOrZero val minSelect: Int,
    @Schema(description = "How many options the guest can choose at most", example = "1")
    @Positive val maxSelect: Int,
    @Schema(description = "The full list of options. Options missing here are removed.")
    @NotEmpty @Valid val options: List<ModifierRequest>,
    @Schema(description = VERSION_DESCRIPTION, example = "0")
    val version: Long,
) {
    fun toCommand(venueId: VenueId, id: ModifierGroupId) =
        UpdateModifierGroupCommand(venueId, id, name, minSelect, maxSelect, options.map { it.toDraft() }, version)
}
