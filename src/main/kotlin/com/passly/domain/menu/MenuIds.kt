package com.passly.domain.menu

import java.util.*

@JvmInline
value class CategoryId(val value: UUID) {
    override fun toString() = value.toString()
    companion object { fun new() = CategoryId(UUID.randomUUID()) }
}

@JvmInline
value class StationId(val value: UUID) {
    override fun toString() = value.toString()
    companion object { fun new() = StationId(UUID.randomUUID()) }
}

@JvmInline
value class MenuItemId(val value: UUID) {
    override fun toString() = value.toString()
    companion object { fun new() = MenuItemId(UUID.randomUUID()) }
}

@JvmInline
value class ModifierGroupId(val value: UUID) {
    override fun toString() = value.toString()
    companion object { fun new() = ModifierGroupId(UUID.randomUUID()) }
}

@JvmInline
value class ModifierId(val value: UUID) {
    override fun toString() = value.toString()
    companion object { fun new() = ModifierId(UUID.randomUUID()) }
}

