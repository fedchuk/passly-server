package com.passly.application.menu

import com.passly.domain.common.ConflictException
import com.passly.domain.common.NotFoundException
import com.passly.domain.menu.CategoryId
import com.passly.domain.menu.CategoryRepository
import com.passly.domain.menu.MenuItem
import com.passly.domain.menu.MenuItemId
import com.passly.domain.menu.MenuItemRepository
import com.passly.domain.menu.ModifierGroupRepository
import com.passly.domain.menu.StationId
import com.passly.domain.menu.StationRepository
import com.passly.domain.venue.Venue
import com.passly.domain.venue.VenueId
import com.passly.domain.venue.VenueRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class MenuItemService(
    private val venues: VenueRepository,
    private val categories: CategoryRepository,
    private val stations: StationRepository,
    private val items: MenuItemRepository,
    private val modifierGroups: ModifierGroupRepository,
) {

    @Transactional(readOnly = true)
    fun get(venueId: VenueId, id: MenuItemId): MenuItem = requireItem(venueId, id)

    @Transactional(readOnly = true)
    fun listInCategory(venueId: VenueId, categoryId: CategoryId): List<MenuItem> {
        requireCategory(venueId, categoryId)
        return items.loadCategoryItems(venueId, categoryId).items
    }

    fun create(command: CreateMenuItemCommand): MenuItem {
        val venue = lockVenue(command.venueId)
        requireCategory(venue.id, command.categoryId)
        command.details.stationId?.let { requireStationInVenue(it, venue.id) }

        val item = items.loadCategoryItems(venue.id, command.categoryId).add(venue.tenantId, command.details)

        items.saveAll(listOf(item))
        return item
    }

    fun update(command: UpdateMenuItemCommand): MenuItem {
        command.details.stationId?.let { requireStationInVenue(it, command.venueId) }

        val item = requireItem(command.venueId, command.itemId)
        if (command.expectedVersion != null && command.expectedVersion != item.version) {
            throw ConflictException("Menu item ${item.name} was changed by someone else. Reload and try again.")
        }
        item.update(command.details)

        items.saveAll(listOf(item))
        return item
    }

    fun changeAvailability(venueId: VenueId, itemId: MenuItemId, available: Boolean): MenuItem {
        val item = requireItem(venueId, itemId)
        if (available) item.removeFromStopList() else item.addToStopList()

        items.saveAll(listOf(item))
        return item
    }

    fun move(command: MoveMenuItemCommand) {
        lockVenue(command.venueId)
        requireCategory(command.venueId, command.targetCategoryId)
        val sourceId = checkNotNull(requireItem(command.venueId, command.itemId).categoryId)
        if (sourceId == command.targetCategoryId) return

        val source = items.loadCategoryItems(command.venueId, sourceId)
        val target = items.loadCategoryItems(command.venueId, command.targetCategoryId)
        val item = source[command.itemId]

        items.saveAll(source.takeOut(item.id) + target.moveIn(item))
    }

    fun reorder(command: ReorderMenuItemsCommand) {
        lockVenue(command.venueId)
        requireCategory(command.venueId, command.categoryId)

        val changed = items.loadCategoryItems(command.venueId, command.categoryId).reorder(command.orderedIds)

        items.saveAll(changed)
    }

    fun archive(venueId: VenueId, itemId: MenuItemId) {
        lockVenue(venueId)
        val categoryId = checkNotNull(requireItem(venueId, itemId).categoryId)

        val changed = items.loadCategoryItems(venueId, categoryId).archive(itemId)

        items.saveAll(changed)
    }

    fun attachModifierGroups(command: AttachModifierGroupsCommand): MenuItem {
        val venueGroups = modifierGroups.findAll(command.venueId).map { it.id }.toSet()
        command.groupIds.firstOrNull { it !in venueGroups }?.let { throw NotFoundException("Modifier group", it) }

        val item = requireItem(command.venueId, command.itemId)
        if (command.expectedVersion != null && command.expectedVersion != item.version) {
            throw ConflictException("Menu item ${item.name} was changed by someone else. Reload and try again.")
        }
        item.attachModifierGroups(command.groupIds)

        items.saveAll(listOf(item))
        return item
    }

    private fun requireItem(venueId: VenueId, id: MenuItemId): MenuItem =
        items.findActive(venueId, id) ?: throw NotFoundException("Menu item", id)

    private fun requireCategory(venueId: VenueId, categoryId: CategoryId) {
        if (categoryId !in categories.loadTree(venueId)) throw NotFoundException("Category", categoryId)
    }

    private fun lockVenue(id: VenueId): Venue =
        venues.findByIdAndLock(id) ?: throw NotFoundException("Venue", id)

    private fun requireStationInVenue(stationId: StationId, venueId: VenueId) {
        if (!stations.existsInVenue(stationId, venueId)) throw NotFoundException("Station", stationId)
    }
}
