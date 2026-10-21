package com.passly.domain.floor

import com.passly.domain.common.ConflictException
import com.passly.domain.common.NotFoundException
import com.passly.domain.common.ValidationException
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId

class FloorPlan(
    val venueId: VenueId,
    areas: Collection<DiningArea>,
    tables: Collection<DiningTable>,
) {
    private val areasById: MutableMap<DiningAreaId, DiningArea> = areas.associateByTo(LinkedHashMap()) { it.id }
    private val tablesById: MutableMap<DiningTableId, DiningTable> = tables.associateByTo(LinkedHashMap()) { it.id }

    init {
        require(areas.all { it.venueId == venueId }) { "All areas must belong to venue $venueId" }
        require(tables.all { it.venueId == venueId && it.areaId in areasById }) {
            "All tables must belong to an area of venue $venueId"
        }
    }

    val areas: List<DiningArea> get() = areasById.values.sortedBy { it.sortOrder }

    val tables: List<DiningTable> get() = areas.flatMap { tablesIn(it.id) }

    fun area(id: DiningAreaId): DiningArea = areasById[id] ?: throw NotFoundException("Area", id)

    fun table(id: DiningTableId): DiningTable = tablesById[id] ?: throw NotFoundException("Table", id)

    fun tablesIn(areaId: DiningAreaId): List<DiningTable> =
        tablesById.values.filter { it.areaId == areaId }.sortedBy { it.sortOrder }

    fun addArea(tenantId: TenantId, name: String): DiningArea {
        if (areasById.size >= MAX_AREAS) throw ValidationException("A venue can have at most $MAX_AREAS areas")
        val validName = DiningArea.validAreaName(name)
        requireFreeAreaName(validName, except = null)
        val area = DiningArea.new(tenantId, venueId, validName, nextOrder(areasById.values.map { it.sortOrder }))
        areasById[area.id] = area
        return area
    }

    fun renameArea(id: DiningAreaId, name: String): DiningArea {
        val area = area(id)
        val validName = DiningArea.validAreaName(name)
        requireFreeAreaName(validName, except = id)
        area.name = validName
        return area
    }

    fun shiftArea(id: DiningAreaId, delta: Int): Set<DiningArea> =
        assignAreaPositions(shifted(areas, area(id), delta))

    fun removeArea(id: DiningAreaId): Set<DiningArea> {
        val area = area(id)
        if (tablesIn(id).isNotEmpty()) {
            throw ConflictException("Area '${area.name}' has tables. Move or delete them first.")
        }
        areasById.remove(id)
        return assignAreaPositions(areas)
    }

    fun addTable(tenantId: TenantId, areaId: DiningAreaId, label: String, seats: Int): DiningTable =
        addTables(tenantId, areaId, listOf(label), seats).single()

    fun addNumberedTables(
        tenantId: TenantId,
        areaId: DiningAreaId,
        numbers: IntRange,
        labelPrefix: String,
        seats: Int,
    ): List<DiningTable> {
        if (numbers.isEmpty()) throw ValidationException("The last number must not be less than the first")
        if (numbers.first < 1) throw ValidationException("Numbers must start at 1 or more")
        if (numbers.count() > MAX_BATCH) throw ValidationException("At most $MAX_BATCH tables can be added at once")
        return addTables(tenantId, areaId, numbers.map { "$labelPrefix$it" }, seats)
    }

    fun updateTable(
        id: DiningTableId,
        areaId: DiningAreaId,
        label: String,
        seats: Int,
        active: Boolean,
    ): Set<DiningTable> {
        val table = table(id)
        area(areaId)
        val validLabel = DiningTable.validLabel(label)
        val validSeats = DiningTable.validSeats(seats)
        if (isLabelTaken(validLabel, except = id)) throw ConflictException("Table $validLabel already exists")

        table.label = validLabel
        table.seats = validSeats
        table.active = active

        val changed = linkedSetOf(table)
        if (table.areaId != areaId) {
            val oldAreaId = table.areaId
            table.sortOrder = nextOrder(tablesIn(areaId).map { it.sortOrder })
            table.areaId = areaId
            changed += assignTablePositions(tablesIn(oldAreaId))
        }
        return changed
    }

    fun shiftTable(id: DiningTableId, delta: Int): Set<DiningTable> {
        val table = table(id)
        return assignTablePositions(shifted(tablesIn(table.areaId), table, delta))
    }

    fun removeTable(id: DiningTableId): Set<DiningTable> {
        val table = table(id)
        tablesById.remove(id)
        return assignTablePositions(tablesIn(table.areaId))
    }

    private fun addTables(tenantId: TenantId, areaId: DiningAreaId, labels: List<String>, seats: Int): List<DiningTable> {
        area(areaId)
        if (tablesById.size + labels.size > MAX_TABLES) {
            throw ValidationException("A venue can have at most $MAX_TABLES tables")
        }
        val validLabels = labels.map { DiningTable.validLabel(it) }
        val validSeats = DiningTable.validSeats(seats)

        if (validLabels.map { it.lowercase() }.toSet().size != validLabels.size) {
            throw ValidationException("The same label is listed twice")
        }
        val taken = validLabels.filter { isLabelTaken(it, except = null) }
        if (taken.size == 1) throw ConflictException("Table ${taken.single()} already exists")
        if (taken.size > 1) throw ConflictException("Tables ${taken.joinToString(", ")} already exist")

        var order = nextOrder(tablesIn(areaId).map { it.sortOrder })
        return validLabels.map { label ->
            DiningTable.new(tenantId, venueId, areaId, label, validSeats, order++).also { tablesById[it.id] = it }
        }
    }

    private fun isLabelTaken(label: String, except: DiningTableId?): Boolean =
        tablesById.values.any { it.id != except && it.label.equals(label, ignoreCase = true) }

    private fun requireFreeAreaName(name: String, except: DiningAreaId?) {
        if (areasById.values.any { it.id != except && it.name.equals(name, ignoreCase = true) }) {
            throw ConflictException("Area '$name' already exists")
        }
    }

    private fun <T> shifted(ordered: List<T>, element: T, delta: Int): List<T> {
        val from = ordered.indexOf(element)
        val to = (from + delta).coerceIn(0, ordered.lastIndex)
        return ordered.toMutableList().apply { add(to, removeAt(from)) }
    }

    private fun assignAreaPositions(ordered: List<DiningArea>): Set<DiningArea> {
        val changed = linkedSetOf<DiningArea>()
        ordered.forEachIndexed { index, area ->
            if (area.sortOrder != index) {
                area.sortOrder = index
                changed += area
            }
        }
        return changed
    }

    private fun assignTablePositions(ordered: List<DiningTable>): Set<DiningTable> {
        val changed = linkedSetOf<DiningTable>()
        ordered.forEachIndexed { index, table ->
            if (table.sortOrder != index) {
                table.sortOrder = index
                changed += table
            }
        }
        return changed
    }

    private fun nextOrder(orders: List<Int>) = (orders.maxOrNull() ?: -1) + 1

    companion object {
        const val MAX_AREAS = 30
        const val MAX_TABLES = 500
        const val MAX_BATCH = 100
    }
}
