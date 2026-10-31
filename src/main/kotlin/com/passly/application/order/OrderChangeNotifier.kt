package com.passly.application.order

interface OrderChangeNotifier {
    fun orderChanged(event: OrderChangedEvent)
}
