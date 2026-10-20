package com.passly.web.admin

import com.passly.security.PasslyUserPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute

@ControllerAdvice(basePackages = ["com.passly.web.admin", "com.passly.web.platform"])
class AdminModelAdvice {

    @ModelAttribute("currentUser")
    fun currentUser(@AuthenticationPrincipal principal: PasslyUserPrincipal?): PasslyUserPrincipal? = principal
}
