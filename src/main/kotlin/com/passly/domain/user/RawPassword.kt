package com.passly.domain.user

import com.passly.domain.common.ValidationException

@JvmInline
value class RawPassword private constructor(val value: String) {
    override fun toString() = "RawPassword(***)"

    companion object {
        const val MIN_LENGTH = 8
        const val MAX_BYTES = 72

        fun of(raw: String): RawPassword {
            if (raw.length < MIN_LENGTH) throw ValidationException("Password must have at least $MIN_LENGTH characters")
            if (raw.toByteArray(Charsets.UTF_8).size > MAX_BYTES) throw ValidationException("Password is too long")
            return RawPassword(raw)
        }
    }
}
