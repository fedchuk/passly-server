package com.passly.web.admin

import com.passly.application.role.CreateRoleCommand
import com.passly.application.role.RenameRoleCommand
import com.passly.application.role.RoleService
import com.passly.domain.common.ConflictException
import com.passly.domain.common.ValidationException
import com.passly.domain.role.VenueRoleId
import com.passly.domain.venue.VenueId
import com.passly.security.PasslyUserPrincipal
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.util.*

@Controller
@RequestMapping("/admin/venues/{venueId}/roles")
class RoleAdminController(
    private val roles: RoleService,
    private val venues: AdminVenues,
) {

    @GetMapping
    fun list(
        @PathVariable venueId: UUID,
        @AuthenticationPrincipal principal: PasslyUserPrincipal,
        model: Model,
    ): String {
        val summaries = roles.list(VenueId(venueId))
        model.addAttribute("venue", venues.find(principal, venueId))
        model.addAttribute("roles", summaries.map { it.toColumnView() })
        model.addAttribute("permissions", GRANTABLE_PERMISSIONS)
        return "admin/roles"
    }

    @PostMapping
    fun create(
        @PathVariable venueId: UUID,
        @RequestParam name: String,
        @RequestParam(required = false) copyOf: UUID?,
        flash: RedirectAttributes,
    ): String = flash.redirectAfter(venueId) {
        val role = roles.create(CreateRoleCommand(VenueId(venueId), name, copyOf = copyOf?.let(::VenueRoleId)))
        "Role '${role.name}' added. Tick what it may do and save."
    }

    @PostMapping("/permissions")
    fun grant(
        @PathVariable venueId: UUID,
        @RequestParam(name = "roles", required = false) roleVersions: List<String>?,
        @RequestParam(required = false) grants: List<String>?,
        flash: RedirectAttributes,
    ): String = flash.redirectAfter(venueId) {
        val changed = roles.grant(grantPermissionsCommand(VenueId(venueId), roleVersions.orEmpty(), grants.orEmpty()))
        if (changed.isEmpty()) "Nothing changed" else "Permissions saved"
    }

    @PostMapping("/{roleId}/rename")
    fun rename(
        @PathVariable venueId: UUID,
        @PathVariable roleId: UUID,
        @RequestParam name: String,
        flash: RedirectAttributes,
    ): String = flash.redirectAfter(venueId) {
        val role = roles.rename(RenameRoleCommand(VenueId(venueId), VenueRoleId(roleId), name))
        "Role renamed to '${role.name}'"
    }

    @PostMapping("/{roleId}/delete")
    fun delete(
        @PathVariable venueId: UUID,
        @PathVariable roleId: UUID,
        flash: RedirectAttributes,
    ): String = flash.redirectAfter(venueId) {
        val role = roles.delete(VenueId(venueId), VenueRoleId(roleId))
        "Role '${role.name}' deleted"
    }

    private fun RedirectAttributes.redirectAfter(venueId: UUID, action: () -> String): String {
        try {
            addFlashAttribute("message", action())
        } catch (e: ConflictException) {
            addFlashAttribute("error", e.message)
        } catch (e: ValidationException) {
            addFlashAttribute("error", e.message)
        } catch (e: OptimisticLockingFailureException) {
            addFlashAttribute("error", "The roles were changed by someone else. Reload and try again.")
        }
        return "redirect:/admin/venues/$venueId/roles"
    }
}
