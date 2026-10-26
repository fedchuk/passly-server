package com.passly.web.admin

import com.passly.domain.menu.ModifierGroup
import com.passly.domain.menu.Station
import java.util.*

data class CategoryRefView(
    val id: UUID,
    val name: String,
)

data class OptionView(
    val id: UUID,
    val name: String,
)

fun Station.toOption() = OptionView(id.value, name)

fun ModifierGroup.toOption() = OptionView(id.value, name)
