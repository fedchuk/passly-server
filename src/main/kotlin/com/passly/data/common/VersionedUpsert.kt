package com.passly.data.common

import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

inline fun <reified E : BaseEntity> JpaRepository<E, UUID>.upsert(
    id: UUID,
    expectedVersion: Long?,
    create: () -> E,
    update: E.() -> Unit,
) {
    if (expectedVersion == null) {
        save(create())
        return
    }
    val entity = getReferenceById(id)
    if (entity.version != expectedVersion) {
        throw OptimisticLockingFailureException("${E::class.simpleName} $id was modified by someone else")
    }
    entity.update()
}
