package com.passly.data.menu

import com.passly.domain.common.Money
import com.passly.domain.common.TaxRate
import com.passly.domain.menu.CategoryId
import com.passly.domain.menu.MenuItem
import com.passly.domain.menu.MenuItemDetails
import com.passly.domain.menu.MenuItemId
import com.passly.domain.menu.ModifierGroupId
import com.passly.domain.menu.StationId
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
        taxRate = TaxRate.of(taxRate),
        stationId = stationId?.let(::StationId),
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
    taxRate = taxRate.percent,
    stationId = stationId?.value,
    sortOrder = sortOrder,
    available = available,
    archived = archived,
).also { entity -> entity.modifierGroupIds = modifierGroupIds.map { it.value }.toMutableList() }

fun MenuItemEntity.updateFrom(item: MenuItem) {
    categoryId = item.categoryId?.value
    name = item.name
    description = item.description
    price = item.price.amount
    taxRate = item.taxRate.percent
    stationId = item.stationId?.value
    sortOrder = item.sortOrder
    available = item.available
    archived = item.archived
    val groupIds = item.modifierGroupIds.map { it.value }
    if (modifierGroupIds != groupIds) modifierGroupIds = groupIds.toMutableList()
}
