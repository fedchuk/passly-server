package com.passly.web.api.menu

import com.passly.application.menu.WaiterMenuService
import com.passly.domain.venue.VenueId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.*

@RestController
@RequestMapping("/api/v1/venues/{venueId}/menu")
@Tag(name = "Menu", description = "The whole menu for the waiter app and notifications about its changes")
class MenuController(
    private val menus: WaiterMenuService,
    private val streams: MenuEventStreams,
) {

    @GetMapping
    @Operation(
        summary = "Whole menu",
        description = "Send the ETag back in If-None-Match. While the menu is the same, the answer is an empty 304.",
    )
    fun menu(@PathVariable venueId: UUID, request: WebRequest): ResponseEntity<MenuResponse>? {
        val id = VenueId(venueId)
        if (request.checkNotModified(etagOf(menus.currentVersion(id)))) return null
        val menu = menus.menu(id)
        return ResponseEntity.ok().eTag(etagOf(menu.menuVersion)).body(menu.toResponse())
    }

    @GetMapping("/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    @Operation(
        summary = "Menu change notifications",
        description = "Server-sent events named 'menu' with MenuChangeNotice data. The first event reports the current " +
                "version. Reload the menu when menuVersion is greater than yours. A comment is sent every 25 seconds " +
                "to keep the connection open.",
    )
    fun events(@PathVariable venueId: UUID): SseEmitter {
        val id = VenueId(venueId)
        return streams.subscribe(id, menus.currentVersion(id))
    }

    private fun etagOf(version: Long) = "\"$version\""
}
