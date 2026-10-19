package com.passly.data.menu

import com.passly.domain.common.Money
import com.passly.domain.menu.Modifier
import com.passly.domain.menu.ModifierGroup
import com.passly.domain.menu.ModifierGroupId
import com.passly.domain.menu.ModifierId
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId

fun ModifierGroupEntity.toDomain() = ModifierGroup.fromStorage(
    id = ModifierGroupId(id),
    tenantId = TenantId(tenantId),
    venueId = VenueId(venueId),
    name = name,
    minSelect = minSelect,
    maxSelect = maxSelect,
    modifiers = modifiers.map { Modifier(ModifierId(it.id), it.name, Money.of(it.priceDelta)) },
    version = checkNotNull(version) { "Stored modifier group $id has no version" },
)

fun ModifierGroup.toNewEntity() = ModifierGroupEntity(
    id = id.value,
    tenantId = tenantId.value,
    venueId = venueId.value,
    name = name,
    minSelect = minSelect,
    maxSelect = maxSelect,
).also { it.modifiers = modifiers.toEmbeddables() }

fun ModifierGroupEntity.updateFrom(group: ModifierGroup) {
    name = group.name
    minSelect = group.minSelect
    maxSelect = group.maxSelect
    modifiers = group.modifiers.toEmbeddables()
}

private fun List<Modifier>.toEmbeddables() =
    map { ModifierEmbeddable(it.id.value, it.name, it.priceDelta.amount) }.toMutableList()
