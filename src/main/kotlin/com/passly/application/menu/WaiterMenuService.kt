package com.passly.application.menu

import com.passly.domain.common.NotFoundException
import com.passly.domain.venue.VenueId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

@Service
class WaiterMenuService(private val query: WaiterMenuQuery) {

    @Transactional(readOnly = true)
    fun currentVersion(venueId: VenueId): Long =
        query.menuVersion(venueId) ?: throw NotFoundException("Venue", venueId)

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun menu(venueId: VenueId): WaiterMenu =
        query.menu(venueId) ?: throw NotFoundException("Venue", venueId)
}
