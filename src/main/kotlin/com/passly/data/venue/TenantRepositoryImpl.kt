package com.passly.data.venue

import com.passly.domain.venue.Tenant
import com.passly.domain.venue.TenantRepository
import org.springframework.stereotype.Repository

@Repository
class TenantRepositoryImpl(
    private val jpa: TenantJpaRepository,
) : TenantRepository {

    override fun save(tenant: Tenant) {
        jpa.save(TenantEntity(tenant.id.value, tenant.name))
    }
}
