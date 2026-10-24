package com.passly.web.admin

import com.passly.domain.menu.Station
import java.util.*

data class StationRowView(
    val id: UUID,
    val name: String,
    val version: Long?,
    val first: Boolean,
    val last: Boolean,
)

fun List<Station>.toRowViews(): List<StationRowView> = mapIndexed { index, station ->
    StationRowView(station.id.value, station.name, station.version, index == 0, index == lastIndex)
}
