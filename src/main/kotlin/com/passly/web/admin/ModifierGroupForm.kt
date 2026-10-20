package com.passly.web.admin

import com.passly.domain.common.Money
import com.passly.domain.menu.ModifierDraft
import com.passly.domain.menu.ModifierGroup
import com.passly.domain.menu.ModifierId
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.util.UUID

class ModifierGroupForm {
    @NotBlank(message = "Name must not be blank")
    @Size(max = 120, message = "Name is too long (max 120)")
    var name: String = ""

    @PositiveOrZero(message = "Must be 0 or more")
    var minSelect: Int = 0

    @Positive(message = "Must be at least 1")
    var maxSelect: Int = 1

    @Valid
    var options: MutableList<ModifierOptionForm> = mutableListOf()

    var version: Long? = null

    fun drafts(): List<ModifierDraft> = options.filterNot { it.isBlank }.map { it.toDraft() }

    companion object {
        private const val EMPTY_ROWS = 2

        fun empty() = ModifierGroupForm().apply {
            repeat(EMPTY_ROWS) { options += ModifierOptionForm() }
        }

        fun of(group: ModifierGroup) = ModifierGroupForm().apply {
            name = group.name
            minSelect = group.minSelect
            maxSelect = group.maxSelect
            options = group.modifiers.map { ModifierOptionForm.of(it.id.value, it.name, it.priceDelta) }.toMutableList()
            version = group.version
        }
    }
}

class ModifierOptionForm {
    var id: UUID? = null

    @Size(max = 120, message = "Option name is too long (max 120)")
    var name: String = ""

    @Pattern(regexp = SIGNED_MONEY, message = "Enter an amount like 0.50 or -0.50")
    var priceDelta: String = ""

    val isBlank: Boolean get() = id == null && name.isBlank() && priceDelta.isBlank()

    fun toDraft() = ModifierDraft(
        name = name,
        priceDelta = Money.of(priceDelta.trim().replace(',', '.').ifEmpty { "0" }),
        id = id?.let(::ModifierId),
    )

    companion object {
        const val SIGNED_MONEY = "^\\s*(-?\\d{1,8}([.,]\\d{1,2})?)?\\s*$"

        fun of(id: UUID, name: String, priceDelta: Money) = ModifierOptionForm().apply {
            this.id = id
            this.name = name
            this.priceDelta = if (priceDelta.amount.signum() == 0) "" else priceDelta.toString()
        }
    }
}
