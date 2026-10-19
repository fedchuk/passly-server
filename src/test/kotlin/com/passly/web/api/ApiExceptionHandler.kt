package com.passly.web.api

import com.passly.domain.common.ConflictException
import com.passly.domain.common.DomainException
import com.passly.domain.common.NotFoundException
import com.passly.domain.common.ValidationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

@RestControllerAdvice(basePackages = ["com.passly.web.api"])
class ApiExceptionHandler : ResponseEntityExceptionHandler() {

    @ExceptionHandler(DomainException::class)
    fun handleDomain(e: DomainException): ProblemDetail {
        val status = when (e) {
            is NotFoundException -> HttpStatus.NOT_FOUND
            is ConflictException -> HttpStatus.CONFLICT
            is ValidationException -> HttpStatus.BAD_REQUEST
        }
        return ProblemDetail.forStatusAndDetail(status, e.message)
    }

    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun handleConcurrentChange(e: OptimisticLockingFailureException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "The data was changed by someone else. Reload and try again.")
}
