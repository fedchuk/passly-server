package com.passly.application.menu

data class MenuChangedEvent(
    val eventId: String,
    val type: String,
    val venueId: String,
    val menuVersion: Long,
    val change: MenuChangeKind,
    val occurredAt: String,
) {
    companion object {
        const val TYPE = "MenuChanged"
    }
}
