package com.passly.data.menu

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MenuItemJpaRepository : JpaRepository<MenuItemEntity, UUID> {
    fun findAllByVenueIdAndCategoryIdAndArchivedFalse(venueId: UUID, categoryId: UUID): List<MenuItemEntity>
    fun findByIdAndVenueIdAndArchivedFalse(id: UUID, venueId: UUID): MenuItemEntity?
    fun existsByCategoryId(categoryId: UUID): Boolean
}
