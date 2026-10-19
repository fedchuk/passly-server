package com.passly.data.menu

import com.passly.data.common.upsert
import com.passly.domain.menu.Category
import com.passly.domain.menu.CategoryRepository
import com.passly.domain.menu.MenuTree
import com.passly.domain.venue.VenueId
import org.springframework.stereotype.Repository

@Repository
class CategoryRepositoryImpl(
    private val jpa: CategoryJpaRepository,
) : CategoryRepository {

    override fun loadTree(venueId: VenueId): MenuTree =
        MenuTree(venueId, jpa.findAllByVenueId(venueId.value).map { it.toDomain() })

    override fun saveAll(categories: Collection<Category>) {
        categories.forEach { category ->
            jpa.upsert(category.id.value, category.version, create = { category.toNewEntity() }, update = { updateFrom(category) })
        }
    }

    override fun delete(category: Category) {
        jpa.deleteById(category.id.value)
    }
}
