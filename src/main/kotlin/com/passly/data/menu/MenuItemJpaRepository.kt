package com.passly.data.menu

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface MenuItemJpaRepository : JpaRepository<MenuItemEntity, UUID> {

    @EntityGraph(attributePaths = ["modifierGroupIds"])
    fun findAllByVenueIdAndCategoryIdAndArchivedFalse(venueId: UUID, categoryId: UUID): List<MenuItemEntity>

    @EntityGraph(attributePaths = ["modifierGroupIds"])
    fun findByIdAndVenueIdAndArchivedFalse(id: UUID, venueId: UUID): MenuItemEntity?

    fun existsByCategoryId(categoryId: UUID): Boolean

    @Query("select count(i) > 0 from MenuItemEntity i where :groupId member of i.modifierGroupIds")
    fun existsWithModifierGroup(@Param("groupId") groupId: UUID): Boolean
}
