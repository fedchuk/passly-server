package com.passly.domain.user

import com.passly.domain.common.ValidationException

@JvmInline
value class Email private constructor(val value: String) {
    override fun toString() = value

    companion object {
        const val MAX_LENGTH = 254
        private val FORMAT = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        fun of(raw: String): Email {
            val normalized = raw.trim().lowercase()
            if (normalized.length > MAX_LENGTH || !FORMAT.matches(normalized)) {
                throw ValidationException("'$raw' is not a valid email")
            }
            return Email(normalized)
        }
    }
}
