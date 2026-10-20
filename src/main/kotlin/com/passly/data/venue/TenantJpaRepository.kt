package com.passly.data.venue

import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface TenantJpaRepository : JpaRepository<TenantEntity, UUID>
