package com.passly.web.api.menu

import com.passly.application.menu.MenuChangeNotifier
import com.passly.application.menu.MenuChangedEvent
import com.passly.domain.venue.VenueId
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

@Component
class MenuEventStreams : MenuChangeNotifier {

    private val subscribers = ConcurrentHashMap<VenueId, MutableSet<SseEmitter>>()
    private val knownVersions = ConcurrentHashMap<VenueId, Long>()

    fun subscribe(venueId: VenueId, currentVersion: Long): SseEmitter {
        val emitter = SseEmitter(STREAM_TIMEOUT_MS)
        val venueSubscribers = subscribers.computeIfAbsent(venueId) { CopyOnWriteArraySet() }
        venueSubscribers += emitter
        emitter.onCompletion { venueSubscribers -= emitter }
        emitter.onError { venueSubscribers -= emitter }
        emitter.onTimeout { emitter.complete() }
        send(emitter, menuEvent(MenuChangeNotice(currentVersion, null)))
        return emitter
    }

    override fun menuChanged(event: MenuChangedEvent) {
        val venueId = VenueId(UUID.fromString(event.venueId))
        if (!rememberIfNewer(venueId, event.menuVersion)) return
        val notice = MenuChangeNotice(event.menuVersion, event.change)
        subscribers[venueId]?.forEach { send(it, menuEvent(notice)) }
    }

    fun knownVersion(venueId: VenueId): Long? = knownVersions[venueId]

    @Scheduled(fixedRate = HEARTBEAT_INTERVAL_MS)
    fun sendHeartbeats() {
        subscribers.values.flatten().forEach { send(it, SseEmitter.event().comment("heartbeat")) }
    }

    private fun rememberIfNewer(venueId: VenueId, version: Long): Boolean {
        var newer = false
        knownVersions.compute(venueId) { _, known ->
            if (known == null || version > known) {
                newer = true
                version
            } else {
                known
            }
        }
        return newer
    }

    private fun menuEvent(notice: MenuChangeNotice): SseEmitter.SseEventBuilder =
        SseEmitter.event()
            .id(notice.menuVersion.toString())
            .name(MENU_EVENT)
            .data(notice, MediaType.APPLICATION_JSON)

    private fun send(emitter: SseEmitter, event: SseEmitter.SseEventBuilder) {
        try {
            emitter.send(event)
        } catch (e: Exception) {
            emitter.completeWithError(e)
        }
    }

    private companion object {
        const val MENU_EVENT = "menu"
        const val STREAM_TIMEOUT_MS = 30L * 60 * 1000
        const val HEARTBEAT_INTERVAL_MS = 25_000L
    }
}
