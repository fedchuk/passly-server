package com.passly.data.user

import com.passly.domain.user.Role
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import java.util.UUID

@Embeddable
class MembershipEmbeddable(
    @Column(name = "tenant_id", nullable = false)
    var tenantId: UUID,

    @Column(name = "venue_id")
    var venueId: UUID?,

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    var role: Role,
)
