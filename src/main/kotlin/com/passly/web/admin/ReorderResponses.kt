package com.passly.web.admin

import com.passly.domain.common.ValidationException
import org.springframework.http.ResponseEntity

fun reordered(action: () -> Unit): ResponseEntity<Void> =
    try {
        action()
        ResponseEntity.noContent().build()
    } catch (e: ValidationException) {
        ResponseEntity.status(422).build()
    }
