package com.passly.web.admin

import com.passly.application.role.RoleService
import com.passly.application.user.ChangeStaffRoleCommand
import com.passly.application.user.HireStaffCommand
import com.passly.application.user.StaffService
import com.passly.domain.common.ConflictException
import com.passly.domain.common.ForbiddenException
import com.passly.domain.common.ValidationException
import com.passly.domain.role.VenueRoleId
import com.passly.domain.user.Email
import com.passly.domain.user.RawPassword
import com.passly.domain.user.UserId
import com.passly.domain.venue.VenueId
import com.passly.security.PasslyUserPrincipal
import com.passly.web.parse
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.util.*

@Controller
@RequestMapping("/admin/venues/{venueId}/staff")
class StaffAdminController(
    private val staff: StaffService,
    private val roles: RoleService,
    private val venues: AdminVenues,
) {

    @GetMapping
    fun list(
        @PathVariable venueId: UUID,
        @AuthenticationPrincipal principal: PasslyUserPrincipal,
        model: Model,
    ): String {
        val venue = venues.find(principal, venueId)
        val assignable = assignableRoles(venue)
        val assignableIds = assignable.map { it.id }.toSet()
        model.addAttribute("venue", venue)
        model.addAttribute("staff", staff.list(VenueId(venueId)).map { it.toRowView(principal.userId, assignableIds) })
        model.addAttribute("roles", assignable)
        return "admin/staff"
    }

    @GetMapping("/new")
    fun newMember(
        @PathVariable venueId: UUID,
        @AuthenticationPrincipal principal: PasslyUserPrincipal,
        model: Model,
    ): String {
        model.addAttribute("form", HireStaffForm())
        return formPage(venueId, principal, model)
    }

    @PostMapping
    fun hire(
        @PathVariable venueId: UUID,
        @Valid @ModelAttribute("form") form: HireStaffForm,
        binding: BindingResult,
        @AuthenticationPrincipal principal: PasslyUserPrincipal,
        model: Model,
        flash: RedirectAttributes,
    ): String {
        val email = binding.parse("email") { Email.of(form.email) }
        val password = binding.parse("password") { RawPassword.of(form.password) }
        val roleId = form.roleId
        if (!binding.hasErrors() && email != null && password != null && roleId != null) {
            try {
                val member = staff.hire(
                    HireStaffCommand(
                        venueId = VenueId(venueId),
                        email = email,
                        displayName = form.displayName,
                        password = password,
                        roleId = VenueRoleId(roleId),
                        hiredBy = UserId(principal.userId),
                    ),
                )
                flash.addFlashAttribute("message", "${member.displayName} joined as ${member.roleName}")
                return listPage(venueId)
            } catch (e: ConflictException) {
                binding.rejectValue("email", "taken", e.message ?: "This email is already used")
            } catch (e: ForbiddenException) {
                binding.rejectValue("roleId", "forbidden", e.message ?: "You cannot give this role")
            } catch (e: ValidationException) {
                binding.reject("invalid", e.message ?: "Invalid data")
            }
        }
        return formPage(venueId, principal, model)
    }

    @PostMapping("/{userId}/role")
    fun changeRole(
        @PathVariable venueId: UUID,
        @PathVariable userId: UUID,
        @RequestParam roleId: UUID,
        @AuthenticationPrincipal principal: PasslyUserPrincipal,
        flash: RedirectAttributes,
    ): String {
        try {
            val member = staff.changeRole(
                ChangeStaffRoleCommand(VenueId(venueId), UserId(userId), VenueRoleId(roleId), UserId(principal.userId)),
            )
            flash.addFlashAttribute("message", "${member.displayName} is now ${member.roleName}")
        } catch (e: ForbiddenException) {
            flash.addFlashAttribute("error", e.message)
        } catch (e: ValidationException) {
            flash.addFlashAttribute("error", e.message)
        }
        return listPage(venueId)
    }

    @PostMapping("/{userId}/dismiss")
    fun dismiss(
        @PathVariable venueId: UUID,
        @PathVariable userId: UUID,
        @AuthenticationPrincipal principal: PasslyUserPrincipal,
        flash: RedirectAttributes,
    ): String {
        if (userId == principal.userId) {
            flash.addFlashAttribute("error", "You cannot dismiss yourself")
            return listPage(venueId)
        }
        staff.dismiss(VenueId(venueId), UserId(userId))
        flash.addFlashAttribute("message", "The staff member no longer works here")
        return listPage(venueId)
    }

    private fun formPage(venueId: UUID, principal: PasslyUserPrincipal, model: Model): String {
        val venue = venues.find(principal, venueId)
        model.addAttribute("venue", venue)
        model.addAttribute("roles", assignableRoles(venue))
        return "admin/staff-form"
    }

    private fun assignableRoles(venue: VenueView): List<RoleOptionView> =
        roles.list(VenueId(venue.id))
            .map { it.role }
            .filter { venue.permissions.containsAll(it.permissions) }
            .map { it.toOptionView() }

    private fun listPage(venueId: UUID) = "redirect:/admin/venues/$venueId/staff"
}
