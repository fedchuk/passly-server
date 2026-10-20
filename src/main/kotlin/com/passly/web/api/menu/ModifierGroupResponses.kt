package com.passly.web.api.menu

import com.passly.domain.menu.ModifierGroup
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

data class ModifierResponse(
    val id: UUID,
    val name: String,
    @Schema(example = "-0.50")
    val priceDelta: String,
)

data class ModifierGroupResponse(
    val id: UUID,
    val name: String,
    val minSelect: Int,
    val maxSelect: Int,
    val options: List<ModifierResponse>,
    val version: Long,
)

fun ModifierGroup.toResponse() = ModifierGroupResponse(
    id = id.value,
    name = name,
    minSelect = minSelect,
    maxSelect = maxSelect,
    options = modifiers.map { ModifierResponse(it.id.value, it.name, it.priceDelta.amount.toPlainString()) },
    version = checkNotNull(version) { "Modifier group $id is not stored yet" },
)
