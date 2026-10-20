package com.passly.web.admin

import com.passly.application.access.AccessService
import com.passly.domain.user.UserId
import com.passly.security.PasslyUserPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class DashboardController(private val access: AccessService) {

    @GetMapping("/admin")
    fun dashboard(@AuthenticationPrincipal principal: PasslyUserPrincipal, model: Model): String {
        model.addAttribute("venues", access.venuesOf(UserId(principal.userId)).map { it.toView() })
        return "admin/dashboard"
    }
}
