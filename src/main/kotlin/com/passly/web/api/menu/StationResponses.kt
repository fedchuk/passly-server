package com.passly.web.api.menu

import com.passly.domain.menu.Station
import java.util.*

data class StationResponse(
    val id: UUID,
    val name: String,
)

fun Station.toResponse() = StationResponse(id.value, name)
