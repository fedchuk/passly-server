package com.passly.data.menu

import com.passly.data.common.upsert
import com.passly.domain.menu.*
import com.passly.domain.venue.VenueId
import org.springframework.stereotype.Repository

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

    override fun findActiveByIds(venueId: VenueId, ids: Collection<MenuItemId>): List<MenuItem> =
        if (ids.isEmpty()) emptyList()
        else jpa.findAllByIdInAndVenueIdAndArchivedFalse(ids.map { it.value }, venueId.value).map { it.toDomain() }

    override fun existsInCategory(categoryId: CategoryId): Boolean =
        jpa.existsByCategoryId(categoryId.value)

    override fun saveAll(items: Collection<MenuItem>) {
        items.forEach { item ->
            jpa.upsert(item.id.value, item.version, create = { item.toNewEntity() }, update = { updateFrom(item) })
        }
    }

    override fun existsWithModifierGroup(groupId: ModifierGroupId): Boolean =
        jpa.existsWithModifierGroup(groupId.value)

    override fun existsWithStation(stationId: StationId): Boolean =
        jpa.existsByStationId(stationId.value)

    override fun countActiveByCategory(venueId: VenueId): Map<CategoryId, Int> =
        jpa.countActiveByCategory(venueId.value).associate { CategoryId(it.categoryId) to it.items.toInt() }
}
