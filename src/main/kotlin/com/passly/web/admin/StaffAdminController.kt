package com.passly.web.admin

import com.passly.application.user.ChangeStaffRoleCommand
import com.passly.application.user.HireStaffCommand
import com.passly.application.user.StaffService
import com.passly.domain.common.ConflictException
import com.passly.domain.common.ValidationException
import com.passly.domain.user.Email
import com.passly.domain.user.RawPassword
import com.passly.domain.user.Role
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
    private val venues: AdminVenues,
) {

    @GetMapping
    fun list(
        @PathVariable venueId: UUID,
        @AuthenticationPrincipal principal: PasslyUserPrincipal,
        model: Model,
    ): String {
        model.addAttribute("venue", venues.find(principal, venueId))
        model.addAttribute("staff", staff.list(VenueId(venueId)).map { it.toRowView(principal.userId) })
        model.addAttribute("roles", ASSIGNABLE_ROLES)
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
        if (!binding.hasErrors() && email != null && password != null) {
            try {
                val member = staff.hire(
                    HireStaffCommand(
                        VenueId(venueId),
                        email,
                        form.displayName,
                        password,
                        form.role
                    )
                )
                flash.addFlashAttribute("message", "${member.displayName} joined as ${form.role.label()}")
                return listPage(venueId)
            } catch (e: ConflictException) {
                binding.rejectValue("email", "taken", e.message ?: "This email is already used")
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
        @RequestParam role: Role,
        @AuthenticationPrincipal principal: PasslyUserPrincipal,
        flash: RedirectAttributes,
    ): String {
        if (userId == principal.userId) {
            flash.addFlashAttribute("error", "You cannot change your own role")
            return listPage(venueId)
        }
        try {
            val member = staff.changeRole(ChangeStaffRoleCommand(VenueId(venueId), UserId(userId), role))
            flash.addFlashAttribute("message", "${member.displayName} is now ${role.label()}")
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
        model.addAttribute("venue", venues.find(principal, venueId))
        model.addAttribute("roles", ASSIGNABLE_ROLES)
        return "admin/staff-form"
    }

    private fun Role.label() = name.lowercase()

    private fun listPage(venueId: UUID) = "redirect:/admin/venues/$venueId/staff"
}
