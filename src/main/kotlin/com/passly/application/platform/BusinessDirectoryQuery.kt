package com.passly.application.platform

interface BusinessDirectoryQuery {
    fun allBusinesses(): List<BusinessSummary>
}
