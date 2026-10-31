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
    @Schema(
        description = "Amount the guest hands over, in the payment currency. Cash may be more than what is due: " +
                "the due is counted and the rest is returned as change in the main currency. A card payment must not be more than the due.",
        example = "20.00",
    )
    @Pattern(regexp = """^\d{1,8}(\.\d{1,2})?$""") val amount: String,
    @Schema(
        description = "ISO 4217 code of the payment currency: the main one or one listed in GET …/currencies. Empty means the main currency.",
        example = "EUR",
    )
    @Pattern(regexp = "^[A-Z]{3}$") val currency: String? = null,
)

data class ApplyDiscountRequest(
    @Schema(description = "One of GET …/discounts")
    val discountId: UUID,
)

data class ApplyVoucherRequest(
    @Schema(description = "Code the guest brings, with or without the dash", example = "ABCD-2345")
    @Size(max = 20) val code: String,
)

data class ServeLinesRequest(
    @Schema(description = "Lines handed to the guests, usually the ready ones")
    @NotEmpty @Size(max = 100) val lineIds: List<UUID>,
)
