package com.passly.web.api.staff

import com.passly.application.user.ChangeStaffRoleCommand
import com.passly.application.user.HireStaffCommand
import com.passly.application.user.StaffService
import com.passly.domain.user.Email
import com.passly.domain.user.RawPassword
import com.passly.domain.user.UserId
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
@RequestMapping("/api/v1/venues/{venueId}/staff")
class StaffController(
    private val service: StaffService,
) {

    @GetMapping
    fun list(@PathVariable venueId: UUID): List<StaffMemberResponse> =
        service.list(VenueId(venueId)).map { it.toResponse() }

    @PostMapping
    fun hire(
        @PathVariable venueId: UUID,
        @Valid @RequestBody request: HireStaffRequest,
    ): ResponseEntity<StaffMemberResponse> {
        val user = service.hire(
            HireStaffCommand(
                venueId = VenueId(venueId),
                email = Email.of(request.email),
                displayName = request.displayName,
                password = RawPassword.of(request.password),
                role = request.role,
            ),
        )
        val location = URI.create("/api/v1/venues/$venueId/staff/${user.id}")
        return ResponseEntity.created(location)
            .body(StaffMemberResponse(user.id.value, user.email.value, user.displayName, request.role))
    }

    @PutMapping("/{userId}/role")
    fun changeRole(
        @PathVariable venueId: UUID,
        @PathVariable userId: UUID,
        @Valid @RequestBody request: ChangeStaffRoleRequest,
    ): StaffMemberResponse {
        val user = service.changeRole(ChangeStaffRoleCommand(VenueId(venueId), UserId(userId), request.role))
        return StaffMemberResponse(user.id.value, user.email.value, user.displayName, request.role)
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun dismiss(@PathVariable venueId: UUID, @PathVariable userId: UUID) {
        service.dismiss(VenueId(venueId), UserId(userId))
    }
}
