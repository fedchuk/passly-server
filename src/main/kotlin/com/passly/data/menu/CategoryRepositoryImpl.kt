package com.passly.data.menu

import com.passly.domain.menu.Category
import com.passly.domain.menu.CategoryRepository
import com.passly.domain.menu.MenuTree
import com.passly.domain.venue.VenueId
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Repository

@Repository
class CategoryRepositoryImpl(
    private val jpa: CategoryJpaRepository,
) : CategoryRepository {

    override fun loadTree(venueId: VenueId): MenuTree =
        MenuTree(venueId, jpa.findAllByVenueId(venueId.value).map { it.toDomain() })

    override fun saveAll(categories: Collection<Category>) {
        categories.forEach { category ->
            if (category.isNew) {
                jpa.save(category.toNewEntity())
            } else {
                updateExisting(category)
            }
        }
    }

    override fun delete(category: Category) {
        jpa.deleteById(category.id.value)
    }

    private fun updateExisting(category: Category) {
        val entity = jpa.getReferenceById(category.id.value)
        if (entity.version != category.version) {
            throw OptimisticLockingFailureException("Category ${category.id} was modified by someone else")
        }
        entity.updateFrom(category)
    }
}
