package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.UserSession
import com.factstore.core.port.outbound.IUserSessionRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface UserSessionRepositoryJpa : JpaRepository<UserSession, UUID> {
    fun findByJti(jti: String): UserSession?
    fun findByUserId(userId: UUID): List<UserSession>

    @Query(
        """
        SELECT s FROM UserSession s
        WHERE s.userId = :userId
          AND s.revokedAt IS NULL
          AND s.expiresAt > :now
        ORDER BY s.lastSeenAt DESC
        """
    )
    fun findActive(@Param("userId") userId: UUID, @Param("now") now: Instant): List<UserSession>

    @Modifying
    @Query("DELETE FROM UserSession s WHERE s.absoluteExpiresAt < :cutoff")
    fun deleteExpired(@Param("cutoff") cutoff: Instant): Int
}

@Component
class UserSessionRepositoryAdapter(private val jpa: UserSessionRepositoryJpa) : IUserSessionRepository {
    override fun save(session: UserSession): UserSession = jpa.save(session)
    override fun findByJti(jti: String): UserSession? = jpa.findByJti(jti)
    override fun findByUserId(userId: UUID): List<UserSession> = jpa.findByUserId(userId)
    override fun findActiveByUserId(userId: UUID, now: Instant): List<UserSession> =
        jpa.findActive(userId, now)
    override fun deleteExpiredBefore(cutoff: Instant): Int = jpa.deleteExpired(cutoff)
}
