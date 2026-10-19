package com.passly.domain.common

sealed class DomainException(message: String) : RuntimeException(message)

class NotFoundException(what: String, id: Any) : DomainException("$what $id not found")

class ConflictException(message: String) : DomainException(message)

class ValidationException(message: String) : DomainException(message)

class UnauthorizedException(message: String) : DomainException(message)
