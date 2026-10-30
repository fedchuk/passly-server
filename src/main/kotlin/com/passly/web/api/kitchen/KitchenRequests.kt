package com.passly.web.api.kitchen

import com.passly.domain.order.PrepStatus
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.util.*

data class MoveLinesRequest(
    val orderId: UUID,
    @Schema(description = "Lines of this order, for example all lines of a ticket")
    @NotEmpty @Size(max = 100) val lineIds: List<UUID>,
    @Schema(description = "IN_PROGRESS, READY or SERVED")
    val status: PrepStatus,
)