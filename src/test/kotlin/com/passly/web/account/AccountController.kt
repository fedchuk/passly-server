package com.passly.web.account

import com.passly.application.auth.ChangePasswordCommand
import com.passly.application.auth.PasswordService
import com.passly.domain.common.ValidationException
import com.passly.domain.user.RawPassword
import com.passly.domain.user.UserId
import com.passly.security.PasslyUserPrincipal
import com.passly.web.parse
import jakarta.servlet.http.HttpServletRequest
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
@RequestMapping("/account/password")
class AccountController(private val passwords: PasswordService) {

    @GetMapping
    fun passwordForm(model: Model): String {
        model.addAttribute("form", ChangePasswordForm())
        return PASSWORD_VIEW
    }

    @PostMapping
    fun changePassword(
        @Valid @ModelAttribute("form") form: ChangePasswordForm,
        binding: BindingResult,
        @AuthenticationPrincipal principal: PasslyUserPrincipal,
        request: HttpServletRequest,
        flash: RedirectAttributes,
    ): String {
        val newPassword = binding.parse("newPassword") { RawPassword.of(form.newPassword) }
        if (!binding.hasFieldErrors("confirmPassword") && form.confirmPassword != form.newPassword) {
            binding.rejectValue("confirmPassword", "mismatch", "Passwords do not match")
        }
        if (binding.hasErrors() || newPassword == null) return PASSWORD_VIEW
        try {
            passwords.change(ChangePasswordCommand(UserId(principal.userId), form.currentPassword, newPassword))
        } catch (e: ValidationException) {
            val field = e.field
            if (field != null) binding.rejectValue(field, "invalid", e.message ?: "Invalid value")
            else binding.reject("invalid", e.message ?: "Invalid data")
            return PASSWORD_VIEW
        }
        request.changeSessionId()
        flash.addFlashAttribute("message", "Password changed. The mobile app will ask you to log in again.")
        return "redirect:/account/password"
    }

    private companion object {
        const val PASSWORD_VIEW = "account/password"
    }
}
