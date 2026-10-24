package com.passly.web.account

import com.passly.application.access.AccessService
import com.passly.application.user.ProfileService
import com.passly.application.user.RenameProfileCommand
import com.passly.domain.common.ValidationException
import com.passly.domain.user.UserId
import com.passly.security.PasslyUserPrincipal
import com.passly.security.SessionPrincipalRefresher
import com.passly.web.admin.toView
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.mvc.support.RedirectAttributes

@Controller
@RequestMapping("/account/profile")
class ProfileController(
    private val profiles: ProfileService,
    private val access: AccessService,
    private val principals: SessionPrincipalRefresher,
) {

    @GetMapping
    fun profile(@AuthenticationPrincipal principal: PasslyUserPrincipal, model: Model): String {
        model.addAttribute("form", ProfileForm.of(profiles.get(UserId(principal.userId))))
        return profilePage(principal, model)
    }

    @PostMapping
    fun rename(
        @Valid @ModelAttribute("form") form: ProfileForm,
        binding: BindingResult,
        @AuthenticationPrincipal principal: PasslyUserPrincipal,
        request: HttpServletRequest,
        response: HttpServletResponse,
        model: Model,
        flash: RedirectAttributes,
    ): String {
        if (binding.hasErrors()) return profilePage(principal, model)
        try {
            profiles.rename(RenameProfileCommand(UserId(principal.userId), form.displayName))
        } catch (e: ValidationException) {
            binding.rejectValue("displayName", "invalid", e.message ?: "Invalid name")
            return profilePage(principal, model)
        }
        principals.refresh(request, response)
        flash.addFlashAttribute("message", "Profile saved")
        return "redirect:/account/profile"
    }

    private fun profilePage(principal: PasslyUserPrincipal, model: Model): String {
        model.addAttribute("email", principal.username)
        model.addAttribute("platformAdmin", principal.platformAdmin)
        model.addAttribute("venues", access.venuesOf(UserId(principal.userId)).map { it.toView() })
        return "account/profile"
    }
}
