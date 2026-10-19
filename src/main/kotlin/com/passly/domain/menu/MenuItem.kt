package com.passly.domain.menu

import com.passly.domain.common.Money
import com.passly.domain.common.TaxRate
import com.passly.domain.common.ValidationException
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId

class MenuItem private constructor(
    val id: MenuItemId,
    val tenantId: TenantId,
    val venueId: VenueId,
    categoryId: CategoryId?,
    details: MenuItemDetails,
    sortOrder: Int,
    available: Boolean,
    archived: Boolean,
    val version: Long?,
) {
    var categoryId: CategoryId? = categoryId
        internal set

    var name: String = details.name
        private set

    var description: String? = details.description
        private set

    var price: Money = details.price
        private set

    var taxRate: TaxRate = details.taxRate
        private set

    var stationId: StationId? = details.stationId
        private set

    var sortOrder: Int = sortOrder
        internal set

    var available: Boolean = available
        private set

    var archived: Boolean = archived
        private set

    val isNew: Boolean get() = version == null

    val details: MenuItemDetails get() = MenuItemDetails(name, description, price, taxRate, stationId)

    fun update(details: MenuItemDetails) {
        requireActive()
        val valid = validated(details)
        name = valid.name
        description = valid.description
        price = valid.price
        taxRate = valid.taxRate
        stationId = valid.stationId
    }

    fun addToStopList() {
        requireActive()
        available = false
    }

    fun removeFromStopList() {
        requireActive()
        available = true
    }

    internal fun archive() {
        requireActive()
        archived = true
        categoryId = null
        sortOrder = 0
    }

    private fun requireActive() {
        if (archived) throw ValidationException("Menu item '$name' is archived")
    }

    override fun equals(other: Any?) = other is MenuItem && other.id == id

    override fun hashCode() = id.hashCode()

    override fun toString() = "MenuItem($id, '$name', category=$categoryId, order=$sortOrder)"

    companion object {
        const val MAX_NAME_LENGTH = 160
        const val MAX_DESCRIPTION_LENGTH = 2000

        internal fun new(
            tenantId: TenantId,
            venueId: VenueId,
            categoryId: CategoryId,
            details: MenuItemDetails,
            sortOrder: Int,
        ) = MenuItem(
            id = MenuItemId.new(),
            tenantId = tenantId,
            venueId = venueId,
            categoryId = categoryId,
            details = validated(details),
            sortOrder = sortOrder,
            available = true,
            archived = false,
            version = null,
        )

        fun fromStorage(
            id: MenuItemId,
            tenantId: TenantId,
            venueId: VenueId,
            categoryId: CategoryId?,
            details: MenuItemDetails,
            sortOrder: Int,
            available: Boolean,
            archived: Boolean,
            version: Long,
        ) = MenuItem(id, tenantId, venueId, categoryId, details, sortOrder, available, archived, version)

        private fun validated(details: MenuItemDetails): MenuItemDetails {
            val name = details.name.trim()
            if (name.isEmpty()) throw ValidationException("Menu item name must not be blank")
            if (name.length > MAX_NAME_LENGTH) {
                throw ValidationException("Menu item name is too long (max $MAX_NAME_LENGTH)")
            }
            val description = details.description?.trim()?.ifEmpty { null }
            if (description != null && description.length > MAX_DESCRIPTION_LENGTH) {
                throw ValidationException("Description is too long (max $MAX_DESCRIPTION_LENGTH)")
            }
            if (details.price.isNegative) throw ValidationException("Price must not be negative")
            return details.copy(name = name, description = description)
        }
    }
}
