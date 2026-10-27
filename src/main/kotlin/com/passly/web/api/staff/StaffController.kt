package com.passly.web.api.staff

import com.passly.application.user.ChangeStaffRoleCommand
import com.passly.application.user.HireStaffCommand
import com.passly.application.user.StaffService
import com.passly.domain.role.VenueRoleId
import com.passly.domain.user.Email
import com.passly.domain.user.RawPassword
import com.passly.domain.user.UserId
import com.passly.domain.venue.VenueId
import com.passly.web.api.auth.userId
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.net.URI
import java.util.*

@RestController
@Tag(name = "Staff", description = "People who work at a venue and their roles")
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
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<StaffMemberResponse> {
        val member = service.hire(
            HireStaffCommand(
                venueId = VenueId(venueId),
                email = Email.of(request.email),
                displayName = request.displayName,
                password = RawPassword.of(request.password),
                roleId = VenueRoleId(request.roleId),
                hiredBy = jwt.userId(),
            ),
        )
        val location = URI.create("/api/v1/venues/$venueId/staff/${member.userId}")
        return ResponseEntity.created(location).body(member.toResponse())
    }

    @PutMapping("/{userId}/role")
    fun changeRole(
        @PathVariable venueId: UUID,
        @PathVariable userId: UUID,
        @Valid @RequestBody request: ChangeStaffRoleRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): StaffMemberResponse =
        service.changeRole(
            ChangeStaffRoleCommand(
                VenueId(venueId),
                UserId(userId),
                VenueRoleId(request.roleId),
                jwt.userId()
            )
        )
            .toResponse()

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun dismiss(@PathVariable venueId: UUID, @PathVariable userId: UUID) {
        service.dismiss(VenueId(venueId), UserId(userId))
    }
}
