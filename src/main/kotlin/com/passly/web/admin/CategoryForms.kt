package com.passly.web.admin

import com.passly.domain.menu.Category
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.*

class NewCategoryForm {
    @NotBlank(message = "Name must not be blank")
    @Size(max = 120, message = "Name is too long (max 120)")
    var name: String = ""

    var parentId: UUID? = null
    var stationId: UUID? = null
}

class EditCategoryForm {
    @NotBlank(message = "Name must not be blank")
    @Size(max = 120, message = "Name is too long (max 120)")
    var name: String = ""

    var active: Boolean = true

    var stationId: UUID? = null

    var version: Long? = null

    companion object {
        fun of(category: Category) = EditCategoryForm().apply {
            name = category.name
            active = category.active
            stationId = category.stationId?.value
            version = category.version
        }
    }
}
