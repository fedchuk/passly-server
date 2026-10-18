package com.passly.domain.menu

import com.passly.domain.venue.VenueId

interface CategoryRepository {
    fun loadTree(venueId: VenueId): MenuTree
    fun saveAll(categories: Collection<Category>)
    fun delete(category: Category)
}
