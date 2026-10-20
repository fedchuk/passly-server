package com.passly.web.admin

import com.passly.application.access.AccessService
import com.passly.domain.common.NotFoundException
import com.passly.domain.user.UserId
import com.passly.security.PasslyUserPrincipal
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class AdminVenues(private val access: AccessService) {

    fun find(principal: PasslyUserPrincipal, venueId: UUID): VenueView =
        access.venuesOf(UserId(principal.userId)).firstOrNull { it.venueId.value == venueId }?.toView()
            ?: throw NotFoundException("Venue", venueId)
}
