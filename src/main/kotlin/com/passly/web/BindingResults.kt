package com.passly.web

import com.passly.domain.common.ValidationException
import org.springframework.validation.BindingResult

fun <T> BindingResult.parse(field: String, block: () -> T): T? =
    try {
        block()
    } catch (e: ValidationException) {
        if (!hasFieldErrors(field)) rejectValue(field, "invalid", e.message ?: "Invalid value")
        null
    }
