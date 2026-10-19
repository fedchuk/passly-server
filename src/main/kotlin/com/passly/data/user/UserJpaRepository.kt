package com.passly.data.user

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface UserJpaRepository : JpaRepository<UserEntity, UUID> {

    @EntityGraph(attributePaths = ["memberships"])
    @Query("select u from UserEntity u where u.id = :id")
    fun findWithMembershipsById(@Param("id") id: UUID): UserEntity?

    @EntityGraph(attributePaths = ["memberships"])
    fun findByEmail(email: String): UserEntity?

    @EntityGraph(attributePaths = ["memberships"])
    @Query(
        """
        select u from UserEntity u
        where u.id in (select m.id from UserEntity m join m.memberships ms where ms.venueId = :venueId)
        """,
    )
    fun findAllByVenueId(@Param("venueId") venueId: UUID): List<UserEntity>
}
