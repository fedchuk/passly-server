package com.passly.data.menu

import com.passly.domain.common.Money
import com.passly.domain.menu.*
import com.passly.domain.tax.VenueTaxRateId
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId

fun MenuItemEntity.toDomain() = MenuItem.fromStorage(
    id = MenuItemId(id),
    tenantId = TenantId(tenantId),
    venueId = VenueId(venueId),
    categoryId = categoryId?.let(::CategoryId),
    details = MenuItemDetails(
        name = name,
        description = description,
        price = Money.of(price),
        stationId = stationId?.let(::StationId),
        taxRateId = taxRateId?.let(::VenueTaxRateId),
    ),
    sortOrder = sortOrder,
    available = available,
    archived = archived,
    modifierGroupIds = modifierGroupIds.map(::ModifierGroupId),
    version = checkNotNull(version) { "Stored menu item $id has no version" },
)

fun MenuItem.toNewEntity() = MenuItemEntity(
    id = id.value,
    tenantId = tenantId.value,
    venueId = venueId.value,
    categoryId = categoryId?.value,
    name = name,
    description = description,
    price = price.amount,
    stationId = stationId?.value,
    taxRateId = taxRateId?.value,
    sortOrder = sortOrder,
    available = available,
    archived = archived,
).also { entity -> entity.modifierGroupIds = modifierGroupIds.map { it.value }.toMutableList() }

fun MenuItemEntity.updateFrom(item: MenuItem) {
    categoryId = item.categoryId?.value
    name = item.name
    description = item.description
    price = item.price.amount
    stationId = item.stationId?.value
    taxRateId = item.taxRateId?.value
    sortOrder = item.sortOrder
    available = item.available
    archived = item.archived
    val groupIds = item.modifierGroupIds.map { it.value }
    if (modifierGroupIds != groupIds) modifierGroupIds = groupIds.toMutableList()
}
