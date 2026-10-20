package com.passly.web.admin

import com.passly.domain.common.NotFoundException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.servlet.ModelAndView

@ControllerAdvice(basePackages = ["com.passly.web.admin"])
class AdminExceptionHandler {

    @ExceptionHandler(NotFoundException::class)
    fun notFound(e: NotFoundException): ModelAndView =
        ModelAndView("error/404", mapOf("message" to e.message), HttpStatus.NOT_FOUND)
}
