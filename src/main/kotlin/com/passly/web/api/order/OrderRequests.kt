package com.passly.web.api.order

import com.passly.domain.menu.MenuItemId
import com.passly.domain.menu.ModifierId
import com.passly.domain.order.OrderLineRequest
import com.passly.domain.order.PaymentMethod
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.*
import java.util.*

data class OrderLineBody(
    val menuItemId: UUID,
    @Schema(example = "2")
    @Min(1) @Max(99) val quantity: Int,
    @Schema(description = "Chosen options. Every modifier group of the item must get from minSelect to maxSelect of its options.")
    val modifierIds: List<UUID> = emptyList(),
    @Schema(description = "Note for the kitchen", example = "No onions")
    @Size(max = 200) val comment: String? = null,
) {
    fun toDomain() = OrderLineRequest(MenuItemId(menuItemId), quantity, modifierIds.map(::ModifierId), comment)
}

data class OpenOrderRequest(
    val tableId: UUID,
    @Schema(example = "2")
    @Min(1) @Max(50) val guests: Int? = null,
    @Schema(description = "The first round. Leave empty to seat the guests and order later.")
    @Valid val lines: List<OrderLineBody> = emptyList(),
)

data class AddRoundRequest(
    @NotEmpty @Valid val lines: List<OrderLineBody>,
)

data class PaymentRequest(
    val method: PaymentMethod,
    @Schema(description = "Amount to take, at most what is still due. Change for cash is given outside the system.", example = "20.00")
    @Pattern(regexp = """^\d{1,8}(\.\d{1,2})?$""") val amount: String,
)
