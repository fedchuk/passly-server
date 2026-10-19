package com.passly.domain.menu

import com.passly.domain.venue.VenueId

interface MenuItemRepository {
    fun loadCategoryItems(venueId: VenueId, categoryId: CategoryId): CategoryItems
    fun findActive(venueId: VenueId, id: MenuItemId): MenuItem?
    fun existsInCategory(categoryId: CategoryId): Boolean
    fun saveAll(items: Collection<MenuItem>)
}
