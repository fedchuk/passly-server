package com.passly.web.api.role

import com.passly.application.role.RoleService
import com.passly.domain.role.VenueRoleId
import com.passly.domain.venue.VenueId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.net.URI
import java.util.*

@RestController
@Tag(name = "Roles", description = "Roles of a venue and what each of them may do. Changed by owners only.")
@RequestMapping("/api/v1/venues/{venueId}/roles")
class RoleController(
    private val service: RoleService,
) {

    @GetMapping
    @Operation(summary = "Roles of the venue", description = "Open to owners and to people who manage the staff")
    fun list(@PathVariable venueId: UUID): List<RoleResponse> =
        service.list(VenueId(venueId)).map { it.toResponse() }

    @GetMapping("/{roleId}")
    fun get(@PathVariable venueId: UUID, @PathVariable roleId: UUID): RoleResponse =
        service.get(VenueId(venueId), VenueRoleId(roleId)).toResponse()

    @PostMapping
    fun create(
        @PathVariable venueId: UUID,
        @Valid @RequestBody request: CreateRoleRequest,
    ): ResponseEntity<RoleResponse> {
        val role = service.create(request.toCommand(VenueId(venueId)))
        return ResponseEntity.created(URI.create("/api/v1/venues/$venueId/roles/${role.id}"))
            .body(service.get(VenueId(venueId), role.id).toResponse())
    }

    @PutMapping("/{roleId}")
    fun update(
        @PathVariable venueId: UUID,
        @PathVariable roleId: UUID,
        @Valid @RequestBody request: UpdateRoleRequest,
    ): RoleResponse {
        service.update(request.toCommand(VenueId(venueId), VenueRoleId(roleId)))
        return service.get(VenueId(venueId), VenueRoleId(roleId)).toResponse()
    }

    @DeleteMapping("/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable venueId: UUID, @PathVariable roleId: UUID) {
        service.delete(VenueId(venueId), VenueRoleId(roleId))
    }
}
