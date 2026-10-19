package com.passly.data.user

import com.passly.data.common.BaseEntity
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.OrderColumn
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "app_user")
class UserEntity(
    id: UUID,

    @Column(name = "email", nullable = false, updatable = false, length = 254)
    val email: String,

    @Column(name = "display_name", nullable = false, length = 120)
    var displayName: String,

    @Column(name = "password_hash", nullable = false)
    var passwordHash: String,

    @Column(name = "platform_admin", nullable = false, updatable = false)
    val platformAdmin: Boolean,

    @Column(name = "enabled", nullable = false)
    var enabled: Boolean,
) : BaseEntity(id) {

    @ElementCollection
    @CollectionTable(name = "membership", joinColumns = [JoinColumn(name = "user_id")])
    @OrderColumn(name = "position")
    var memberships: MutableList<MembershipEmbeddable> = mutableListOf()
}
