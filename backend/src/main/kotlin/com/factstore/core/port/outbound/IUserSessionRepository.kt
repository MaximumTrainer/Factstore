package com.factstore.core.port.outbound

import com.factstore.core.domain.UserSession
import java.time.Instant
import java.util.UUID

interface IUserSessionRepository {
    fun save(session: UserSession): UserSession
    fun findByJti(jti: String): UserSession?
    fun findByUserId(userId: UUID): List<UserSession>
    /** Sessions still worth showing a user or an administrator. */
    fun findActiveByUserId(userId: UUID, now: Instant): List<UserSession>
    fun deleteExpiredBefore(cutoff: Instant): Int
}
