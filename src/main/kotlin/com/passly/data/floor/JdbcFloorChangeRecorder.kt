package com.passly.data.floor

import com.passly.application.floor.FloorChangeRecorder
import com.passly.domain.common.NotFoundException
import com.passly.domain.venue.VenueId
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

@Repository
class JdbcFloorChangeRecorder(private val jdbc: JdbcClient) : FloorChangeRecorder {

    override fun recordChange(venueId: VenueId) {
        val updated = jdbc.sql("update venue set floor_version = floor_version + 1 where id = :id")
            .param("id", venueId.value)
            .update()
        if (updated == 0) throw NotFoundException("Venue", venueId)
    }
}
