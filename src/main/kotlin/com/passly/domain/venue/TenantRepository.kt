package com.passly.domain.venue

interface TenantRepository {
    fun save(tenant: Tenant)
}
