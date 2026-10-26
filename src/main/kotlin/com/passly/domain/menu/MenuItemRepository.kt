package com.passly.domain.menu

import com.passly.domain.venue.VenueId

interface MenuItemRepository {
    fun loadCategoryItems(venueId: VenueId, categoryId: CategoryId): CategoryItems
    fun findActive(venueId: VenueId, id: MenuItemId): MenuItem?
    fun findActiveByIds(venueId: VenueId, ids: Collection<MenuItemId>): List<MenuItem>
    fun existsInCategory(categoryId: CategoryId): Boolean
    fun saveAll(items: Collection<MenuItem>)
    fun existsWithModifierGroup(groupId: ModifierGroupId): Boolean
    fun existsWithStation(stationId: StationId): Boolean
    fun countActiveByCategory(venueId: VenueId): Map<CategoryId, Int>
}
