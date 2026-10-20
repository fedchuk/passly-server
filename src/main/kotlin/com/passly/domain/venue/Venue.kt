package com.passly.domain.venue

import com.passly.domain.common.ValidationException

class Venue(
    val id: VenueId,
    val tenantId: TenantId,
    val name: String,
) {
    companion object {
        const val MAX_NAME_LENGTH = 160

        fun create(tenantId: TenantId, name: String) = Venue(VenueId.new(), tenantId, validName(name, "Venue"))
    }
}

internal fun validName(name: String, what: String, maxLength: Int = Venue.MAX_NAME_LENGTH): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) throw ValidationException("$what name must not be blank")
    if (trimmed.length > maxLength) throw ValidationException("$what name is too long (max $maxLength)")
    return trimmed
}
