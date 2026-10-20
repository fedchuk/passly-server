package com.passly.web.admin

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class LoginController {

    @GetMapping("/login")
    fun login(): String = "login"

    @GetMapping("/")
    fun root(): String = "redirect:/admin"
}
