package com.passly.web.api.kitchen

import com.passly.application.kitchen.KitchenService
import com.passly.application.kitchen.MoveLinesCommand
import com.passly.domain.menu.StationId
import com.passly.domain.order.OrderId
import com.passly.domain.order.OrderLineId
import com.passly.domain.venue.VenueId
import com.passly.web.api.order.OrderEventStreams
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.*

@RestController
@RequestMapping("/api/v1/venues/{venueId}/kitchen")
@Tag(name = "Kitchen display", description = "Tickets of one station and the progress of their lines")
class KitchenApiController(
    private val kitchen: KitchenService,
    private val streams: OrderEventStreams,
) {

    @GetMapping("/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    @Operation(
        summary = "Changes of orders as they happen",
        description = "The same 'order' events as GET …/orders/events. Reload the tickets of your station when its id is " +
                "in stationIds.",
    )
    fun events(@PathVariable venueId: UUID): SseEmitter = streams.subscribe(VenueId(venueId))

    @GetMapping("/stations/{stationId}/tickets")
    @Operation(
        summary = "Tickets of a station",
        description = "One ticket per round of a table, with the lines this station prepares. Lines already served and " +
                "lines sent more than 24 hours ago are left out. Oldest first.",
    )
    fun tickets(@PathVariable venueId: UUID, @PathVariable stationId: UUID): List<TicketResponse> =
        kitchen.tickets(VenueId(venueId), StationId(stationId)).map { it.toResponse() }

    @PostMapping("/line-status")
    @Operation(
        summary = "Move lines forward",
        description = "Sets the status of the given lines of one order. Lines only move forward: " +
                "NEW → IN_PROGRESS → READY → SERVED, and steps may be skipped. A line already at or past the status is left " +
                "as it is, so repeating the request is safe. Works on closed orders too, for venues where guests pay first. " +
                "Returns the lines that changed.",
    )
    fun move(@PathVariable venueId: UUID, @Valid @RequestBody request: MoveLinesRequest): List<MovedLineResponse> =
        kitchen.moveLines(
            MoveLinesCommand(
                VenueId(venueId),
                OrderId(request.orderId),
                request.lineIds.map(::OrderLineId),
                request.status
            ),
        ).map { MovedLineResponse(it.id.value, it.status, it.statusChangedAt) }
}
