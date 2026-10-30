package com.passly.domain.order

enum class PrepStatus {
    NEW,
    IN_PROGRESS,
    READY,
    SERVED;

    val next: PrepStatus? get() = entries.getOrNull(ordinal + 1)

    fun isBefore(other: PrepStatus): Boolean = ordinal < other.ordinal
}
