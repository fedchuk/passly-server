package com.passly.web.api.order

import com.passly.application.order.AddRoundCommand
import com.passly.application.order.OpenOrderCommand
import com.passly.application.order.OrderService
import com.passly.domain.floor.DiningTableId
import com.passly.domain.order.OrderId
import com.passly.domain.venue.VenueId
import com.passly.web.api.auth.userId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.net.URI
import java.util.*

@RestController
@RequestMapping("/api/v1/venues/{venueId}/orders")
@Tag(name = "Orders", description = "Orders at tables. One open order per table for the whole visit, sent in rounds.")
class OrderController(
    private val orders: OrderService,
) {

    @GetMapping
    @Operation(
        summary = "Open orders",
        description = "Orders that are not closed yet, the oldest first. Tables without one are free."
    )
    fun openOrders(@PathVariable venueId: UUID): List<OrderResponse> =
        orders.listOpen(VenueId(venueId)).map { it.toResponse() }

    @GetMapping("/{id}")
    @Operation(summary = "One order with all its rounds")
    fun get(@PathVariable venueId: UUID, @PathVariable id: UUID): OrderResponse =
        orders.get(VenueId(venueId), OrderId(id)).toResponse()

    @PostMapping
    @Operation(
        summary = "Open a table",
        description = "Starts the order of a table, optionally with the first round. Prices, names and options are " +
                "copied into the order. Fails with 409 when the table already has an open order or is inactive, and when " +
                "an item is on the stop list or no longer in the menu: unavailableItemIds lists them and nothing is saved.",
    )

    fun openTable(
        @PathVariable venueId: UUID,
        @Valid @RequestBody request: OpenOrderRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<OrderResponse> {
        val order = orders.open(
            OpenOrderCommand(
                venueId = VenueId(venueId),
                tableId = DiningTableId(request.tableId),
                waiterId = jwt.userId(),
                guests = request.guests,
                lines = request.lines.map { it.toDomain() },
            ),
        )
        val location = URI.create("/api/v1/venues/$venueId/orders/${order.id}")
        return ResponseEntity.created(location).body(order.toResponse())
    }

    @PostMapping("/{id}/rounds")
    @Operation(
        summary = "Send the next round",
        description = "Adds lines to an open order as one round. The whole round is rejected with 409 and " +
                "unavailableItemIds when an item cannot be ordered now.",
    )
    fun addRound(
        @PathVariable venueId: UUID,
        @PathVariable id: UUID,
        @Valid @RequestBody request: AddRoundRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): OrderResponse =
        orders.addRound(
            AddRoundCommand(VenueId(venueId), OrderId(id), jwt.userId(), request.lines.map { it.toDomain() }),
        ).toResponse()
}
