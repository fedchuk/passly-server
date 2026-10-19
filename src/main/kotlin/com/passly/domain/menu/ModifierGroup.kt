package com.passly.domain.menu

import com.passly.domain.common.ValidationException
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId

class ModifierGroup private constructor(
    val id: ModifierGroupId,
    val tenantId: TenantId,
    val venueId: VenueId,
    name: String,
    minSelect: Int,
    maxSelect: Int,
    modifiers: List<Modifier>,
    val version: Long?,
) {
    var name: String = name
        private set

    var minSelect: Int = minSelect
        private set

    var maxSelect: Int = maxSelect
        private set

    var modifiers: List<Modifier> = modifiers
        private set

    val isNew: Boolean get() = version == null

    fun rename(newName: String) {
        name = validName(newName)
    }

    fun changeLimits(min: Int, max: Int) {
        validateLimits(min, max, modifiers.size)
        minSelect = min
        maxSelect = max
    }

    fun replaceModifiers(drafts: List<ModifierDraft>) {
        val replaced = buildModifiers(drafts, existing = modifiers.associateBy { it.id })
        validateLimits(minSelect, maxSelect, replaced.size)
        modifiers = replaced
    }

    fun select(selectedIds: List<ModifierId>): List<Modifier> {
        if (selectedIds.toSet().size != selectedIds.size) {
            throw ValidationException("The same option of '$name' is selected more than once")
        }
        val byId = modifiers.associateBy { it.id }
        val selected = selectedIds.map { byId[it] ?: throw ValidationException("Option $it does not belong to '$name'") }
        if (selected.size !in minSelect..maxSelect) {
            throw ValidationException("'$name' requires from $minSelect to $maxSelect options, got ${selected.size}")
        }
        return selected
    }

    override fun equals(other: Any?) = other is ModifierGroup && other.id == id

    override fun hashCode() = id.hashCode()

    override fun toString() = "ModifierGroup($id, '$name', $minSelect..$maxSelect, ${modifiers.size} options)"

    companion object {
        const val MAX_NAME_LENGTH = 120
        const val MAX_OPTIONS = 50

        fun create(
            tenantId: TenantId,
            venueId: VenueId,
            name: String,
            minSelect: Int,
            maxSelect: Int,
            drafts: List<ModifierDraft>,
        ): ModifierGroup {
            val modifiers = buildModifiers(drafts, existing = emptyMap())
            validateLimits(minSelect, maxSelect, modifiers.size)
            return ModifierGroup(
                id = ModifierGroupId.new(),
                tenantId = tenantId,
                venueId = venueId,
                name = validName(name),
                minSelect = minSelect,
                maxSelect = maxSelect,
                modifiers = modifiers,
                version = null,
            )
        }

        fun fromStorage(
            id: ModifierGroupId,
            tenantId: TenantId,
            venueId: VenueId,
            name: String,
            minSelect: Int,
            maxSelect: Int,
            modifiers: List<Modifier>,
            version: Long,
        ) = ModifierGroup(id, tenantId, venueId, name, minSelect, maxSelect, modifiers, version)

        private fun buildModifiers(drafts: List<ModifierDraft>, existing: Map<ModifierId, Modifier>): List<Modifier> {
            if (drafts.isEmpty()) throw ValidationException("A modifier group needs at least one option")
            if (drafts.size > MAX_OPTIONS) throw ValidationException("A modifier group can have at most $MAX_OPTIONS options")

            val keptIds = drafts.mapNotNull { it.id }
            if (keptIds.toSet().size != keptIds.size) throw ValidationException("The same option is listed twice")
            keptIds.firstOrNull { it !in existing }?.let { throw ValidationException("Unknown option $it") }

            val modifiers = drafts.map { draft ->
                Modifier(id = draft.id ?: ModifierId.new(), name = validName(draft.name), priceDelta = draft.priceDelta)
            }
            val names = modifiers.map { it.name.lowercase() }
            if (names.toSet().size != names.size) throw ValidationException("Option names must be unique")
            return modifiers
        }

        private fun validateLimits(min: Int, max: Int, optionCount: Int) {
            if (min < 0) throw ValidationException("Minimum selection must not be negative")
            if (max < 1) throw ValidationException("Maximum selection must be at least 1")
            if (min > max) throw ValidationException("Minimum selection must not exceed the maximum")
            if (min > optionCount) {
                throw ValidationException("Minimum selection $min is more than the $optionCount available options")
            }
        }

        private fun validName(name: String): String {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) throw ValidationException("Name must not be blank")
            if (trimmed.length > MAX_NAME_LENGTH) throw ValidationException("Name is too long (max $MAX_NAME_LENGTH)")
            return trimmed
        }
    }
}
