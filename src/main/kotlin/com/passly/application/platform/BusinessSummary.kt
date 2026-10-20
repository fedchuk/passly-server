package com.passly.application.platform

import java.time.LocalDate
import java.util.*

data class BusinessSummary(
    val id: UUID,
    val name: String,
    val venues: Int,
    val owners: String,
    val createdOn: LocalDate,
)
