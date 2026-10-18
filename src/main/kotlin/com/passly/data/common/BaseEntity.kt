package com.passly.data.common

import jakarta.persistence.Column
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PreUpdate
import jakarta.persistence.Version
import org.hibernate.Hibernate
import java.time.Instant
import java.util.UUID

@MappedSuperclass
abstract class BaseEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),
) {
    @Version
    @Column(name = "version")
    var version: Long? = null
        protected set

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
        protected set

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
        protected set

    @PreUpdate
    protected fun onPreUpdate() {
        updatedAt = Instant.now()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        return id == (other as BaseEntity).id
    }

    override fun hashCode(): Int = id.hashCode()
}
