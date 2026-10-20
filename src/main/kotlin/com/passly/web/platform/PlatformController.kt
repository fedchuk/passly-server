package com.passly.web.platform

import com.passly.application.platform.BusinessDirectoryQuery
import com.passly.application.venue.OnboardingService
import com.passly.application.venue.RegisterBusinessCommand
import com.passly.domain.common.ConflictException
import com.passly.domain.common.ValidationException
import com.passly.domain.user.Email
import com.passly.domain.user.RawPassword
import com.passly.web.parse
import jakarta.validation.Valid
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.mvc.support.RedirectAttributes

@Controller
@RequestMapping("/platform")
class PlatformController(
    private val directory: BusinessDirectoryQuery,
    private val onboarding: OnboardingService,
) {

    @GetMapping
    fun businesses(model: Model): String {
        model.addAttribute("businesses", directory.allBusinesses())
        return "platform/businesses"
    }

    @GetMapping("/businesses/new")
    fun newBusiness(model: Model): String {
        model.addAttribute("form", RegisterBusinessForm())
        return "platform/business-form"
    }

    @PostMapping("/businesses")
    fun register(
        @Valid @ModelAttribute("form") form: RegisterBusinessForm,
        binding: BindingResult,
        flash: RedirectAttributes,
    ): String {
        val email = binding.parse("ownerEmail") { Email.of(form.ownerEmail) }
        val password = binding.parse("ownerPassword") { RawPassword.of(form.ownerPassword) }
        if (binding.hasErrors() || email == null || password == null) return "platform/business-form"
        try {
            onboarding.registerBusiness(
                RegisterBusinessCommand(form.businessName, form.venueName, email, form.ownerName, password),
            )
        } catch (e: ConflictException) {
            binding.rejectValue("ownerEmail", "taken", e.message ?: "This email is already used")
            return "platform/business-form"
        } catch (e: ValidationException) {
            binding.reject("invalid", e.message ?: "Invalid data")
            return "platform/business-form"
        }
        flash.addFlashAttribute("message", "${form.businessName} is ready. ${form.ownerName} can log in as $email")
        return "redirect:/platform"
    }
}
