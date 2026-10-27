package com.passly.application.menu

import com.passly.domain.common.ConflictException
import com.passly.domain.common.NotFoundException
import com.passly.domain.common.ValidationException
import com.passly.domain.menu.*
import com.passly.domain.tax.TaxRatePreset
import com.passly.domain.tax.TaxRateRepository
import com.passly.domain.tax.VenueTaxRateId
import com.passly.domain.venue.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class CategoryService(
    private val venues: VenueRepository,
    private val categories: CategoryRepository,
    private val stations: StationRepository,
    private val items: MenuItemRepository,
    private val menuChanges: MenuChangeRecorder,
    private val taxRates: TaxRateRepository,
    private val settings: VenueSettingsRepository,
) {

    @Transactional(readOnly = true)
    fun getTree(venueId: VenueId): MenuTree {
        requireVenue(venueId)
        return categories.loadTree(venueId)
    }

    fun create(command: CreateCategoryCommand): Category {
        val venue = lockVenue(command.venueId)
        command.stationId?.let { requireStationInVenue(it, venue.id) }

        command.taxRateId?.let { requireTaxRateInVenue(it, venue.id) }
        val taxRateId = command.taxRateId ?: if (command.parentId == null) defaultTaxRate(venue) else null

        val category = categories.loadTree(venue.id)
            .add(venue.tenantId, command.name, command.parentId, command.stationId, taxRateId)

        categories.saveAll(listOf(category))
        menuChanges.recordChange(venue.id, MenuChangeKind.CATEGORIES)
        return category
    }

    fun update(command: UpdateCategoryCommand): Category {
        requireVenue(command.venueId)
        command.stationId?.let { requireStationInVenue(it, command.venueId) }
        command.taxRateId?.let { requireTaxRateInVenue(it, command.venueId) }

        val category = categories.loadTree(command.venueId)[command.categoryId]
        if (command.expectedVersion != null && command.expectedVersion != category.version) {
            throw ConflictException("Category ${category.name} was changed by someone else. Reload and try again.")
        }

        category.apply {
            rename(command.name)
            assignStation(command.stationId)
            assignTaxRate(command.taxRateId ?: taxRateId.takeIf { parentId == null })
            if (command.active) activate() else deactivate()
        }

        categories.saveAll(listOf(category))
        menuChanges.recordChange(command.venueId, MenuChangeKind.CATEGORIES)
        return category
    }

    fun move(command: MoveCategoryCommand) {
        lockVenue(command.venueId)
        val changed = categories.loadTree(command.venueId)
            .move(command.categoryId, command.newParentId, command.position)
        categories.saveAll(changed)
        menuChanges.recordChange(command.venueId, MenuChangeKind.CATEGORIES)
    }

    fun shift(venueId: VenueId, categoryId: CategoryId, delta: Int) {
        lockVenue(venueId)
        val changed = categories.loadTree(venueId).shift(categoryId, delta)
        categories.saveAll(changed)
        menuChanges.recordChange(venueId, MenuChangeKind.CATEGORIES)
    }

    fun reorder(command: ReorderCategoriesCommand) {
        lockVenue(command.venueId)
        val changed = categories.loadTree(command.venueId).reorder(command.parentId, command.orderedIds)
        categories.saveAll(changed)
        menuChanges.recordChange(command.venueId, MenuChangeKind.CATEGORIES)
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
        menuChanges.recordChange(venueId, MenuChangeKind.CATEGORIES)
    }

    private fun requireVenue(id: VenueId): Venue =
        venues.findById(id) ?: throw NotFoundException("Venue", id)

    private fun lockVenue(id: VenueId): Venue =
        venues.findByIdAndLock(id) ?: throw NotFoundException("Venue", id)

    private fun requireTaxRateInVenue(taxRateId: VenueTaxRateId, venueId: VenueId) {
        taxRates.load(venueId)[taxRateId]
    }

    private fun defaultTaxRate(venue: Venue): VenueTaxRateId {
        val list = taxRates.load(venue.id)
        if (list.rates.isEmpty()) {
            val country = settings.find(venue.id)?.country ?: CountryCode.FRANCE
            taxRates.saveAll(list.addMissing(venue.tenantId, TaxRatePreset.forCountry(country)))
        }
        return list.rates.firstOrNull()?.id
            ?: throw ValidationException("Add a tax rate in the venue settings first", "taxRateId")
    }

    private fun requireStationInVenue(stationId: StationId, venueId: VenueId) {
        if (!stations.existsInVenue(stationId, venueId)) throw NotFoundException("Station", stationId)
    }
}
