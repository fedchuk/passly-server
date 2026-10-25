package com.passly.application.order

import com.passly.domain.floor.DiningArea
import com.passly.domain.floor.DiningTable
import com.passly.domain.order.Order

data class TableStatus(
    val table: DiningTable,
    val area: DiningArea,
    val order: Order?,
)
