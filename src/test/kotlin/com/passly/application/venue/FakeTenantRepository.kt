package com.passly.application.venue

import com.passly.domain.venue.Tenant
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.TenantRepository

class FakeTenantRepository : TenantRepository {
    val stored = linkedMapOf<TenantId, Tenant>()

    override fun save(tenant: Tenant) {
        stored[tenant.id] = tenant
    }
}
