package com.passly.data.user

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.util.*

@Embeddable
class MembershipEmbeddable(
    @Column(name = "tenant_id", nullable = false)
    var tenantId: UUID,

    @Column(name = "venue_id")
    var venueId: UUID?,

    @Column(name = "role_id")
    var roleId: UUID?,
)
