package com.passly.application.discount

import com.passly.domain.common.ConflictException
import com.passly.domain.common.NotFoundException
import com.passly.domain.discount.DiscountRepository
import com.passly.domain.discount.VenueDiscount
import com.passly.domain.discount.VenueDiscountId
import com.passly.domain.venue.Venue
import com.passly.domain.venue.VenueId
import com.passly.domain.venue.VenueRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class DiscountService(
    private val venues: VenueRepository,
    private val discounts: DiscountRepository,
) {

    @Transactional(readOnly = true)
    fun list(venueId: VenueId): List<VenueDiscount> {
        venues.findById(venueId) ?: throw NotFoundException("Venue", venueId)
        return discounts.load(venueId).discounts
    }

    fun create(command: CreateDiscountCommand): VenueDiscount {
        val venue = lockVenue(command.venueId)
        val discount = discounts.load(venue.id).add(venue.tenantId, command.name, command.percent)
        discounts.saveAll(listOf(discount))
        return discount
    }

    fun change(command: ChangeDiscountCommand): VenueDiscount {
        lockVenue(command.venueId)
        val list = discounts.load(command.venueId)
        val current = list[command.discountId]
        if (command.expectedVersion != null && command.expectedVersion != current.version) {
            throw ConflictException("Discount '${current.name}' was changed by someone else. Reload and try again.")
        }
        val discount = list.change(command.discountId, command.name, command.percent)
        discounts.saveAll(listOf(discount))
        return discount
    }

    fun delete(venueId: VenueId, discountId: VenueDiscountId): VenueDiscount {
        lockVenue(venueId)
        val list = discounts.load(venueId)
        val discount = list[discountId]
        val renumbered = list.remove(discountId)
        discounts.delete(discount)
        discounts.saveAll(renumbered)
        return discount
    }

    private fun lockVenue(id: VenueId): Venue =
        venues.findByIdAndLock(id) ?: throw NotFoundException("Venue", id)
}
