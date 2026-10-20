package com.passly.application.venue

data class VenueOverview(
    val categories: Int,
    val items: Int,
    val stopListed: Int,
    val modifierGroups: Int,
    val staff: Int,
)
