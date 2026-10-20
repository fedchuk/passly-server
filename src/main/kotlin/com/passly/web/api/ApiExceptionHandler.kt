package com.passly.web.api

import com.passly.domain.common.*
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.*
import org.springframework.http.ProblemDetail.forStatusAndDetail
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

@RestControllerAdvice(basePackages = ["com.passly.web.api"])
class ApiExceptionHandler : ResponseEntityExceptionHandler() {

    @ExceptionHandler(DomainException::class)
    fun handleDomain(e: DomainException): ProblemDetail {
        val status = when (e) {
            is NotFoundException -> HttpStatus.NOT_FOUND
            is ConflictException -> HttpStatus.CONFLICT
            is ValidationException -> HttpStatus.BAD_REQUEST
            is UnauthorizedException -> HttpStatus.UNAUTHORIZED
        }
        return forStatusAndDetail(status, e.message)
    }

    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun handleConcurrentChange(e: OptimisticLockingFailureException): ProblemDetail =
        forStatusAndDetail(HttpStatus.CONFLICT, "The data was changed by someone else. Reload and try again.")

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest
    ): ResponseEntity<Any>? {
        val problem = ex.body.apply {
            detail = "Validation Failed"
            setProperty("errors", ex.bindingResult.fieldErrors.groupBy({ it.field }, { it.defaultMessage ?: "is invalid" }))
        }
        return handleExceptionInternal(ex, problem, headers, status, request)
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleIntegrityViolation(e: DataIntegrityViolationException): ProblemDetail =
        forStatusAndDetail(HttpStatus.CONFLICT, "The operation conflicts with related data. Reload and try again.")
}
