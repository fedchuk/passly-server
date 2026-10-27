package com.passly.domain.menu

import com.passly.domain.common.ConflictException
import com.passly.domain.common.NotFoundException
import com.passly.domain.common.ValidationException
import com.passly.domain.tax.VenueTaxRateId
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId

class MenuTree(
    val venueId: VenueId,
    categories: Collection<Category>,
) {
    private val byId: MutableMap<CategoryId, Category> =
        categories.associateByTo(LinkedHashMap()) { it.id }

    init {
        require(categories.all { it.venueId == venueId }) { "All categories must belong to venue $venueId" }
    }

    val categories: Collection<Category> get() = byId.values

    operator fun get(id: CategoryId): Category = byId[id] ?: throw NotFoundException("Category", id)

    operator fun contains(id: CategoryId): Boolean = id in byId

    fun children(parentId: CategoryId?): List<Category> =
        byId.values.filter { it.parentId == parentId }.sortedBy { it.sortOrder }

    fun descendantsOf(id: CategoryId): Set<CategoryId> {
        val result = linkedSetOf<CategoryId>()
        val queue = ArrayDeque(children(id))
        while (queue.isNotEmpty()) {
            val next = queue.removeFirst()
            if (result.add(next.id)) queue.addAll(children(next.id))
        }
        return result
    }

    fun effectiveStation(id: CategoryId): StationId? {
        var current: Category? = get(id)
        while (current != null) {
            current.stationId?.let { return it }
            current = current.parentId?.let { byId[it] }
        }
        return null
    }

    fun effectiveTaxRate(id: CategoryId): VenueTaxRateId? {
        var current: Category? = get(id)
        while (current != null) {
            current.taxRateId?.let { return it }
            current = current.parentId?.let { byId[it] }
        }
        return null
    }

    fun add(
        tenantId: TenantId,
        name: String,
        parentId: CategoryId? = null,
        stationId: StationId? = null,
        taxRateId: VenueTaxRateId? = null,
    ): Category {
        parentId?.let { get(it) }
        if (parentId == null && stationId == null) {
            throw ValidationException("Choose a station for a top-level category", "stationId")
        }
        if (parentId == null && taxRateId == null) {
            throw ValidationException("Choose a tax rate for a top-level category", "taxRateId")
        }
        val siblings = children(parentId)
        val category = Category.new(
            tenantId = tenantId,
            venueId = venueId,
            parentId = parentId,
            name = name,
            sortOrder = (siblings.maxOfOrNull { it.sortOrder } ?: -1) + 1,
            stationId = stationId,
            taxRateId = taxRateId,
        )
        byId[category.id] = category
        return category
    }

    fun move(id: CategoryId, newParentId: CategoryId?, position: Int? = null): Set<Category> {
        val category = get(id)
        if (newParentId != null) {
            get(newParentId)
            if (newParentId == id || newParentId in descendantsOf(id)) {
                throw ConflictException("A category cannot be moved into itself or one of its subcategories")
            }
        }

        if (newParentId == null && category.stationId == null) {
            throw ConflictException("Choose a station for '${category.name}' before moving it to the top level")
        }

        val oldParentId = category.parentId
        if (newParentId == null && oldParentId != null && category.taxRateId == null) {
            category.taxRateId = effectiveTaxRate(oldParentId)
        }
        val newSiblings = children(newParentId).filter { it.id != id }.toMutableList()
        val index = (position ?: newSiblings.size).coerceIn(0, newSiblings.size)
        newSiblings.add(index, category)

        val changed = linkedSetOf<Category>()
        if (oldParentId != newParentId) {
            category.parentId = newParentId
            changed += category
            changed += assignPositions(children(oldParentId))
        }
        changed += assignPositions(newSiblings)
        return changed
    }

    fun shift(id: CategoryId, delta: Int): Set<Category> {
        val category = get(id)
        val siblings = children(category.parentId)
        val current = siblings.indexOf(category)
        val target = (current + delta).coerceIn(0, siblings.lastIndex)
        return if (target == current) emptySet() else move(id, category.parentId, target)
    }

    fun reorder(parentId: CategoryId?, orderedIds: List<CategoryId>): Set<Category> {
        val siblings = children(parentId)
        if (orderedIds.toSet().size != orderedIds.size) {
            throw ValidationException("orderedIds contains duplicates")
        }
        if (siblings.map { it.id }.toSet() != orderedIds.toSet()) {
            throw ValidationException("orderedIds must list exactly the current children of the parent")
        }
        return assignPositions(orderedIds.map { get(it) })
    }

    fun removeLeaf(id: CategoryId): Set<Category> {
        val category = get(id)
        if (children(id).isNotEmpty()) {
            throw ConflictException("Category '${category.name}' has subcategories. Move or delete them first.")
        }
        byId.remove(id)
        return assignPositions(children(category.parentId))
    }

    private fun assignPositions(ordered: List<Category>): Set<Category> {
        val changed = linkedSetOf<Category>()
        ordered.forEachIndexed { index, category ->
            if (category.sortOrder != index) {
                category.sortOrder = index
                changed += category
            }
        }
        return changed
    }
}
