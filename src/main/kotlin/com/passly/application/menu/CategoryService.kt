package com.passly.application.menu

import com.passly.domain.common.ConflictException
import com.passly.domain.common.NotFoundException
import com.passly.domain.menu.Category
import com.passly.domain.menu.CategoryId
import com.passly.domain.menu.CategoryRepository
import com.passly.domain.menu.MenuItemRepository
import com.passly.domain.menu.MenuTree
import com.passly.domain.menu.StationId
import com.passly.domain.menu.StationRepository
import com.passly.domain.venue.Venue
import com.passly.domain.venue.VenueId
import com.passly.domain.venue.VenueRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class CategoryService(
    private val venues: VenueRepository,
    private val categories: CategoryRepository,
    private val stations: StationRepository,
    private val items: MenuItemRepository,
) {

    @Transactional(readOnly = true)
    fun getTree(venueId: VenueId): MenuTree {
        requireVenue(venueId)
        return categories.loadTree(venueId)
    }

    fun create(command: CreateCategoryCommand): Category {
        val venue = lockVenue(command.venueId)
        command.stationId?.let { requireStationInVenue(it, venue.id) }

        val category = categories.loadTree(venue.id)
            .add(venue.tenantId, command.name, command.parentId, command.stationId)

        categories.saveAll(listOf(category))
        return category
    }

    fun update(command: UpdateCategoryCommand): Category {
        requireVenue(command.venueId)
        command.stationId?.let { requireStationInVenue(it, command.venueId) }

        val category = categories.loadTree(command.venueId)[command.categoryId]
        if (command.expectedVersion != null && command.expectedVersion != category.version) {
            throw ConflictException("Category ${category.name} was changed by someone else. Reload and try again.")
        }

        category.apply {
            rename(command.name)
            assignStation(command.stationId)
            if (command.active) activate() else deactivate()
        }

        categories.saveAll(listOf(category))
        return category
    }

    fun move(command: MoveCategoryCommand) {
        lockVenue(command.venueId)
        val changed = categories.loadTree(command.venueId)
            .move(command.categoryId, command.newParentId, command.position)
        categories.saveAll(changed)
    }

    fun shift(venueId: VenueId, categoryId: CategoryId, delta: Int) {
        lockVenue(venueId)
        val changed = categories.loadTree(venueId).shift(categoryId, delta)
        categories.saveAll(changed)
    }

    fun reorder(command: ReorderCategoriesCommand) {
        lockVenue(command.venueId)
        val changed = categories.loadTree(command.venueId).reorder(command.parentId, command.orderedIds)
        categories.saveAll(changed)
    }

    fun delete(venueId: VenueId, categoryId: CategoryId) {
        lockVenue(venueId)
        val tree = categories.loadTree(venueId)
        val category = tree[categoryId]
        if (items.existsInCategory(categoryId)) {
            throw ConflictException("Category '${category.name}' has menu items. Move or archive them first.")
        }
        val reordered = tree.removeLeaf(categoryId)

        categories.delete(category)
        categories.saveAll(reordered)
    }

    private fun requireVenue(id: VenueId): Venue =
        venues.findById(id) ?: throw NotFoundException("Venue", id)

    private fun lockVenue(id: VenueId): Venue =
        venues.findByIdAndLock(id) ?: throw NotFoundException("Venue", id)

    private fun requireStationInVenue(stationId: StationId, venueId: VenueId) {
        if (!stations.existsInVenue(stationId, venueId)) throw NotFoundException("Station", stationId)
    }
}
