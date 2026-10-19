package com.passly.data.menu

import com.passly.domain.menu.CategoryId
import com.passly.domain.menu.CategoryItems
import com.passly.domain.menu.MenuItem
import com.passly.domain.menu.MenuItemId
import com.passly.domain.menu.MenuItemRepository
import com.passly.domain.venue.VenueId
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Repository
import kotlin.collections.map

@Repository
class MenuItemRepositoryImpl(
    private val jpa: MenuItemJpaRepository,
) : MenuItemRepository {

    override fun loadCategoryItems(venueId: VenueId, categoryId: CategoryId) = CategoryItems(
        venueId,
        categoryId,
        jpa.findAllByVenueIdAndCategoryIdAndArchivedFalse(venueId.value, categoryId.value).map { it.toDomain() },
    )

    override fun findActive(venueId: VenueId, id: MenuItemId): MenuItem? =
        jpa.findByIdAndVenueIdAndArchivedFalse(id.value, venueId.value)?.toDomain()

    override fun existsInCategory(categoryId: CategoryId): Boolean =
        jpa.existsByCategoryId(categoryId.value)

    override fun saveAll(items: Collection<MenuItem>) {
        items.forEach { item ->
            if (item.isNew) jpa.save(item.toNewEntity()) else updateExisting(item)
        }
    }

    private fun updateExisting(item: MenuItem) {
        val entity = jpa.getReferenceById(item.id.value)
        if (entity.version != item.version) {
            throw OptimisticLockingFailureException("Menu item ${item.id} was modified by someone else")
        }
        entity.updateFrom(item)
    }
}
