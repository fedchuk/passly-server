package com.passly.domain.menu

fun MenuTree.stationFor(item: MenuItem): StationId? =
    item.stationId ?: item.categoryId?.let { effectiveStation(it) }
