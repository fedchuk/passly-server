package com.passly.domain.menu

import com.passly.domain.common.ConflictException
import com.passly.domain.common.NotFoundException
import com.passly.domain.common.ValidationException
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId

class StationList(
    val venueId: VenueId,
    stations: Collection<Station>,
) {
    private val byId: MutableMap<StationId, Station> = stations.associateByTo(LinkedHashMap()) { it.id }

    init {
        require(stations.all { it.venueId == venueId }) { "All stations must belong to venue $venueId" }
    }

    val stations: List<Station> get() = byId.values.sortedBy { it.sortOrder }

    operator fun get(id: StationId): Station = byId[id] ?: throw NotFoundException("Station", id)

    fun add(tenantId: TenantId, name: String): Station {
        if (byId.size >= MAX_STATIONS) throw ValidationException("A venue can have at most $MAX_STATIONS stations")
        val validName = Station.validStationName(name)
        requireFreeName(validName, except = null)
        val station = Station.new(tenantId, venueId, validName, (byId.values.maxOfOrNull { it.sortOrder } ?: -1) + 1)
        byId[station.id] = station
        return station
    }

    fun rename(id: StationId, name: String): Station {
        val station = get(id)
        val validName = Station.validStationName(name)
        requireFreeName(validName, except = id)
        station.name = validName
        return station
    }

    fun shift(id: StationId, delta: Int): Set<Station> {
        val ordered = stations.toMutableList()
        val from = ordered.indexOf(get(id))
        val to = (from + delta).coerceIn(0, ordered.lastIndex)
        ordered.add(to, ordered.removeAt(from))
        return assignPositions(ordered)
    }

    fun remove(id: StationId): Set<Station> {
        get(id)
        byId.remove(id)
        return assignPositions(stations)
    }

    private fun requireFreeName(name: String, except: StationId?) {
        if (byId.values.any { it.id != except && it.name.equals(name, ignoreCase = true) }) {
            throw ConflictException("Station '$name' already exists")
        }
    }

    private fun assignPositions(ordered: List<Station>): Set<Station> {
        val changed = linkedSetOf<Station>()
        ordered.forEachIndexed { index, station ->
            if (station.sortOrder != index) {
                station.sortOrder = index
                changed += station
            }
        }
        return changed
    }

    companion object {
        const val MAX_STATIONS = 20
        val DEFAULT_NAMES = listOf("Kitchen", "Bar")
    }
}