package com.factstore.application.auth

import com.factstore.application.ActorResolver
import com.factstore.core.domain.AuditEventType
import com.factstore.core.domain.AuthProvider
import com.factstore.core.domain.MemberRole
import com.factstore.core.domain.SessionRevocationReason
import com.factstore.core.domain.UserSession
import com.factstore.core.domain.security.RoleModel
import com.factstore.core.port.inbound.IAuditService
import com.factstore.core.port.outbound.IOrganisationMembershipRepository
import com.factstore.core.port.outbound.IUserRepository
import com.factstore.core.port.outbound.IUserSessionRepository
import com.factstore.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * The session lifecycle (#156 FR-3): issue, resolve, refresh, revoke.
 *
 * A session is a row, not just a token. That is what makes logout and administrative
 * revocation take effect on the next request, and what lets a user's sessions be listed.
 */
@Service
@Transactional
class SessionService(
    private val sessionRepository: IUserSessionRepository,
    private val userRepository: IUserRepository,
    private val membershipRepository: IOrganisationMembershipRepository,
    private val tokenService: SessionTokenService,
    private val auditService: IAuditService,
    private val actorResolver: ActorResolver,
    @Value("\${security.session.lifetime-seconds:3600}") private val lifetimeSeconds: Long,
    @Value("\${security.session.absolute-lifetime-seconds:43200}") private val absoluteLifetimeSeconds: Long,
    @Value("\${security.session.idle-timeout-seconds:1800}") private val idleTimeoutSeconds: Long
) {

    private val log = LoggerFactory.getLogger(SessionService::class.java)
    private val random = SecureRandom()

    fun issue(
        userId: UUID,
        orgSlug: String?,
        provider: AuthProvider,
        sourceIp: String? = null,
        userAgent: String? = null
    ): IssuedSession {
        val now = Instant.now()
        val jti = newJti()
        val session = sessionRepository.save(
            UserSession(
                jti = jti,
                userId = userId,
                orgSlug = orgSlug,
                provider = provider,
                createdAt = now,
                lastSeenAt = now,
                expiresAt = now.plusSeconds(lifetimeSeconds),
                absoluteExpiresAt = now.plusSeconds(absoluteLifetimeSeconds),
                sourceIp = sourceIp,
                userAgent = userAgent?.take(512)
            )
        )
        val email = userRepository.findById(userId)?.email
        val token = tokenService.issue(userId.toString(), jti, email, session.expiresAt)

        auditService.record(
            eventType = AuditEventType.USER_SIGNED_IN,
            actor = email ?: userId.toString(),
            payload = mapOf(
                "userId" to userId.toString(),
                "orgSlug" to orgSlug,
                "provider" to provider.name,
                "sessionId" to jti,
                "sourceIp" to sourceIp
            )
        )
        log.info("Session $jti issued for user $userId (org=$orgSlug, provider=$provider)")
        return IssuedSession(token = token, session = session, expiresAt = session.expiresAt)
    }

    /**
     * Resolves a presented token to a principal, touching `lastSeenAt`.
     *
     * The role is read from the membership table on **every** request rather than taken from a
     * token claim, so a role change takes effect immediately (FR-5.4).
     */
    fun resolve(token: String): AuthenticatedUser? {
        val verified = tokenService.verify(token) ?: return null
        val session = sessionRepository.findByJti(verified.sessionId) ?: return null
        val now = Instant.now()

        if (session.isRevoked) {
            log.debug("Session ${session.jti} rejected: revoked (${session.revokedReason})")
            return null
        }
        if (session.isExpiredAt(now)) return null
        if (session.isIdleAt(now, idleTimeoutSeconds)) {
            session.revoke(SessionRevocationReason.IDLE_TIMEOUT, now)
            sessionRepository.save(session)
            log.debug("Session ${session.jti} rejected: idle beyond ${idleTimeoutSeconds}s")
            return null
        }

        val userId = runCatching { UUID.fromString(verified.subject) }.getOrNull() ?: return null
        if (userId != session.userId) return null
        val user = userRepository.findById(userId) ?: return null

        val role = resolveRole(userId, session.orgSlug)

        session.lastSeenAt = now
        sessionRepository.save(session)

        return AuthenticatedUser(
            userId = user.id,
            email = user.email,
            name = user.name,
            orgSlug = session.orgSlug,
            role = role,
            sessionId = session.jti,
            sessionExpiresAt = session.expiresAt,
            authorities = RoleModel.authoritiesFor(role)
        )
    }

    /**
     * Extends a session without a fresh trip to the IdP, up to the absolute ceiling
     * (FR-3.2). Refresh cannot outlive `absoluteExpiresAt`, so a session cannot be renewed
     * indefinitely.
     */
    fun refresh(token: String): IssuedSession? {
        val principal = resolve(token) ?: return null
        val session = sessionRepository.findByJti(principal.sessionId) ?: return null
        val now = Instant.now()

        val proposed = now.plusSeconds(lifetimeSeconds)
        session.expiresAt = if (proposed.isAfter(session.absoluteExpiresAt)) session.absoluteExpiresAt else proposed
        session.lastSeenAt = now
        val saved = sessionRepository.save(session)

        val newToken = tokenService.issue(
            principal.userId.toString(), session.jti, principal.email, saved.expiresAt
        )
        return IssuedSession(token = newToken, session = saved, expiresAt = saved.expiresAt)
    }

    fun revoke(jti: String, reason: SessionRevocationReason): Boolean {
        val session = sessionRepository.findByJti(jti) ?: return false
        if (session.isRevoked) return false
        session.revoke(reason)
        sessionRepository.save(session)
        auditService.record(
            eventType = AuditEventType.USER_SIGNED_OUT,
            actor = actorResolver.current(),
            payload = mapOf(
                "userId" to session.userId.toString(),
                "sessionId" to jti,
                "reason" to reason.name
            )
        )
        log.info("Session $jti revoked ($reason)")
        return true
    }

    fun revokeAllForUser(userId: UUID, reason: SessionRevocationReason): Int {
        val now = Instant.now()
        val active = sessionRepository.findActiveByUserId(userId, now)
        active.forEach { session ->
            session.revoke(reason, now)
            sessionRepository.save(session)
        }
        if (active.isNotEmpty()) {
            auditService.record(
                eventType = AuditEventType.USER_SESSIONS_REVOKED,
                actor = actorResolver.current(),
                payload = mapOf(
                    "userId" to userId.toString(),
                    "sessionCount" to active.size,
                    "reason" to reason.name
                )
            )
        }
        return active.size
    }

    @Transactional(readOnly = true)
    fun activeSessions(userId: UUID): List<UserSession> {
        if (userRepository.findById(userId) == null) throw NotFoundException("User not found: $userId")
        return sessionRepository.findActiveByUserId(userId, Instant.now())
            .filter { !it.isIdleAt(Instant.now(), idleTimeoutSeconds) }
    }

    /** Switches the organisation a session is acting in (FR-5.2). */
    fun switchOrganisation(jti: String, orgSlug: String): AuthenticatedUser? {
        val session = sessionRepository.findByJti(jti) ?: return null
        val membership = membershipRepository.findByOrgSlugAndUserId(orgSlug, session.userId)
            ?: return null
        session.orgSlug = orgSlug
        sessionRepository.save(session)
        val user = userRepository.findById(session.userId) ?: return null
        return AuthenticatedUser(
            userId = user.id,
            email = user.email,
            name = user.name,
            orgSlug = orgSlug,
            role = membership.role,
            sessionId = jti,
            sessionExpiresAt = session.expiresAt,
            authorities = RoleModel.authoritiesFor(membership.role)
        )
    }

    /**
     * The role for this user in this organisation. A federated sign-in with no membership gets
     * the documented safe default rather than anything privileged (FR-5.3).
     */
    private fun resolveRole(userId: UUID, orgSlug: String?): MemberRole {
        if (orgSlug == null) return RoleModel.DEFAULT_ROLE_FOR_NEW_MEMBER
        return membershipRepository.findByOrgSlugAndUserId(orgSlug, userId)?.role
            ?: RoleModel.DEFAULT_ROLE_FOR_NEW_MEMBER
    }

    private fun newJti(): String {
        val bytes = ByteArray(24)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    val sessionLifetimeSeconds: Long get() = lifetimeSeconds
}

data class IssuedSession(val token: String, val session: UserSession, val expiresAt: Instant)

/** The resolved principal for a signed-in person. */
data class AuthenticatedUser(
    val userId: UUID,
    val email: String,
    val name: String,
    val orgSlug: String?,
    val role: MemberRole,
    val sessionId: String,
    val sessionExpiresAt: Instant,
    val authorities: Set<String>
)
