package com.passly.domain.common

import java.util.*

sealed class DomainException(message: String) : RuntimeException(message)

class NotFoundException(what: String, id: Any) : DomainException("$what $id not found")

class ConflictException(message: String) : DomainException(message)

class ValidationException(message: String, val field: String? = null) : DomainException(message)

class UnauthorizedException(message: String) : DomainException(message)

class ForbiddenException(message: String) : DomainException(message)

class UnavailableItemsException(val itemIds: List<UUID>, names: List<String>) :
    DomainException("Not available now: ${names.joinToString(", ")}. Reload the menu and try again.")
