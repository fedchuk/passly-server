package com.passly.data.menu

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.*

interface MenuItemJpaRepository : JpaRepository<MenuItemEntity, UUID> {

    @EntityGraph(attributePaths = ["modifierGroupIds"])
    fun findAllByVenueIdAndCategoryIdAndArchivedFalse(venueId: UUID, categoryId: UUID): List<MenuItemEntity>

    @EntityGraph(attributePaths = ["modifierGroupIds"])
    fun findByIdAndVenueIdAndArchivedFalse(id: UUID, venueId: UUID): MenuItemEntity?

    @EntityGraph(attributePaths = ["modifierGroupIds"])
    fun findAllByIdInAndVenueIdAndArchivedFalse(ids: Collection<UUID>, venueId: UUID): List<MenuItemEntity>

    fun existsByCategoryId(categoryId: UUID): Boolean

    fun existsByStationId(stationId: UUID): Boolean

    @Query("select count(i) > 0 from MenuItemEntity i where :groupId member of i.modifierGroupIds")
    fun existsWithModifierGroup(@Param("groupId") groupId: UUID): Boolean

    @Query(
        "select i.categoryId as categoryId, count(i) as items from MenuItemEntity i " +
                "where i.venueId = :venueId and i.archived = false and i.categoryId is not null group by i.categoryId",
    )
    fun countActiveByCategory(@Param("venueId") venueId: UUID): List<CategoryItemCount>
}
