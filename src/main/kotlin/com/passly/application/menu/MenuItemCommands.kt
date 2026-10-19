package com.passly.application.menu

import com.passly.domain.menu.CategoryId
import com.passly.domain.menu.MenuItemDetails
import com.passly.domain.menu.MenuItemId
import com.passly.domain.venue.VenueId

data class CreateMenuItemCommand(
    val venueId: VenueId,
    val categoryId: CategoryId,
    val details: MenuItemDetails,
)

data class UpdateMenuItemCommand(
    val venueId: VenueId,
    val itemId: MenuItemId,
    val details: MenuItemDetails,
    val expectedVersion: Long? = null,
)

data class MoveMenuItemCommand(
    val venueId: VenueId,
    val itemId: MenuItemId,
    val targetCategoryId: CategoryId,
)

data class ReorderMenuItemsCommand(
    val venueId: VenueId,
    val categoryId: CategoryId,
    val orderedIds: List<MenuItemId>,
)
