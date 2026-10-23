package com.passly.web.admin

import com.passly.domain.floor.DiningTable
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.util.*

class AreaForm {
    @NotBlank(message = "Area name must not be blank")
    @Size(max = 80, message = "Area name is too long (max 80)")
    var name: String = ""

    var version: Long? = null
}

class SingleTableForm {
    @NotBlank(message = "Table label must not be blank")
    @Size(max = 20, message = "Table label is too long (max 20)")
    var label: String = ""

    var seats: Int = 2
}

class NumberedTablesForm {
    @Size(max = 16, message = "Prefix is too long (max 16)")
    var labelPrefix: String = ""

    var from: Int = 1

    var to: Int = 0

    var seats: Int = 4
}

class EditTableForm {
    @NotBlank(message = "Table label must not be blank")
    @Size(max = 20, message = "Table label is too long (max 20)")
    var label: String = ""

    var seats: Int = 4

    @NotNull(message = "Choose an area")
    var areaId: UUID? = null

    var active: Boolean = true

    var version: Long? = null

    companion object {
        fun of(table: DiningTable) = EditTableForm().apply {
            label = table.label
            seats = table.seats
            areaId = table.areaId.value
            active = table.active
            version = table.version
        }
    }
}
