package com.passly.web.api.order

import com.passly.application.order.OrderChangeNotifier
import com.passly.application.order.OrderChangedEvent
import com.passly.domain.venue.VenueId
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

@Component
class OrderEventStreams : OrderChangeNotifier {

    private val subscribers = ConcurrentHashMap<VenueId, MutableSet<SseEmitter>>()

    fun subscribe(venueId: VenueId): SseEmitter {
        val emitter = SseEmitter(STREAM_TIMEOUT_MS)
        val venueSubscribers = subscribers.computeIfAbsent(venueId) { CopyOnWriteArraySet() }
        venueSubscribers += emitter
        emitter.onCompletion { venueSubscribers -= emitter }
        emitter.onError { venueSubscribers -= emitter }
        emitter.onTimeout { emitter.complete() }
        send(emitter, SseEmitter.event().comment("connected"))
        return emitter
    }

    override fun orderChanged(event: OrderChangedEvent) {
        val notice = OrderChangeNotice(
            orderId = UUID.fromString(event.orderId),
            tableId = UUID.fromString(event.tableId),
            change = event.change,
            stationIds = event.stationIds.map(UUID::fromString),
        )
        subscribers[VenueId(UUID.fromString(event.venueId))]?.forEach {
            send(it, SseEmitter.event().id(event.eventId).name(ORDER_EVENT).data(notice, MediaType.APPLICATION_JSON))
        }
    }

    fun subscriberCount(venueId: VenueId): Int = subscribers[venueId]?.size ?: 0

    @Scheduled(fixedRate = HEARTBEAT_INTERVAL_MS)
    fun sendHeartbeats() {
        subscribers.values.flatten().forEach { send(it, SseEmitter.event().comment("heartbeat")) }
    }

    private fun send(emitter: SseEmitter, event: SseEmitter.SseEventBuilder) {
        try {
            emitter.send(event)
        } catch (e: Exception) {
            emitter.completeWithError(e)
        }
    }

    private companion object {
        const val ORDER_EVENT = "order"
        const val STREAM_TIMEOUT_MS = 30L * 60 * 1000
        const val HEARTBEAT_INTERVAL_MS = 25_000L
    }
}
