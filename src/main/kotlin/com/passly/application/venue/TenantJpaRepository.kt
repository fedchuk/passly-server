package com.passly.application.venue

import com.passly.data.venue.TenantEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface TenantJpaRepository : JpaRepository<TenantEntity, UUID>
