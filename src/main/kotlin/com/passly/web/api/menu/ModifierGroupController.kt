package com.passly.web.api.menu

import com.passly.application.menu.ModifierGroupService
import com.passly.domain.menu.ModifierGroupId
import com.passly.domain.venue.VenueId
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

@RestController
@RequestMapping("/api/v1/venues/{venueId}/modifier-groups")
class ModifierGroupController(
    private val service: ModifierGroupService,
) {

    @GetMapping
    fun list(@PathVariable venueId: UUID): List<ModifierGroupResponse> =
        service.list(VenueId(venueId)).map { it.toResponse() }

    @GetMapping("/{id}")
    fun get(@PathVariable venueId: UUID, @PathVariable id: UUID): ModifierGroupResponse =
        service.get(VenueId(venueId), ModifierGroupId(id)).toResponse()

    @PostMapping
    fun create(
        @PathVariable venueId: UUID,
        @Valid @RequestBody request: CreateModifierGroupRequest,
    ): ResponseEntity<ModifierGroupResponse> {
        val group = service.create(request.toCommand(VenueId(venueId)))
        val location = URI.create("/api/v1/venues/$venueId/modifier-groups/${group.id}")
        return ResponseEntity.created(location).body(get(venueId, group.id.value))
    }

    @PutMapping("/{id}")
    fun update(
        @PathVariable venueId: UUID,
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateModifierGroupRequest,
    ): ModifierGroupResponse {
        service.update(request.toCommand(VenueId(venueId), ModifierGroupId(id)))
        return get(venueId, id)
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable venueId: UUID, @PathVariable id: UUID) {
        service.delete(VenueId(venueId), ModifierGroupId(id))
    }
}
