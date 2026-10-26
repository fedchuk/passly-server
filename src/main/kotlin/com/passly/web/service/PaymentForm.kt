package com.passly.web.service

import com.passly.domain.order.PaymentMethod
import java.util.*

class PaymentForm {
    var orderId: UUID? = null
    var method: PaymentMethod? = null
    var amount: String = ""
    var currency: String? = null
}
