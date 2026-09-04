package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/** How a person signed in. */
enum class AuthProvider { OIDC, GITHUB, DEV }

enum class SessionRevocationReason { LOGOUT, ADMIN_REVOKED, SUPERSEDED, IDLE_TIMEOUT }

/**
 * A signed-in session (#156 FR-3).
 *
 * The token a client holds carries only this row's [jti]; everything that decides whether the
 * session is still good lives here, so revocation takes effect on the very next request rather
 * than whenever the token happens to expire.
 */
@Entity
@Table(name = "user_sessions")
class UserSession(
    @Id
    val id: UUID = UUID.randomUUID(),

    /** The token identifier. Opaque, random, and the only session detail a token carries. */
    @Column(nullable = false, length = 64)
    val jti: String,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    /** The organisation this session is acting in; every request is scoped to it (FR-5.2). */
    @Column(name = "org_slug", nullable = true, length = 255)
    var orgSlug: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    val provider: AuthProvider,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "last_seen_at", nullable = false)
    var lastSeenAt: Instant = Instant.now(),

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,

    @Column(name = "absolute_expires_at", nullable = false)
    val absoluteExpiresAt: Instant,

    @Column(name = "revoked_at", nullable = true)
    var revokedAt: Instant? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "revoked_reason", nullable = true, length = 64)
    var revokedReason: SessionRevocationReason? = null,

    @Column(name = "source_ip", nullable = true, length = 64)
    val sourceIp: String? = null,

    @Column(name = "user_agent", nullable = true, length = 512)
    val userAgent: String? = null
) {
    val isRevoked: Boolean get() = revokedAt != null

    fun isExpiredAt(now: Instant): Boolean = !now.isBefore(expiresAt) || !now.isBefore(absoluteExpiresAt)

    /** Idle timeout: a session untouched for longer than [idleTimeoutSeconds] is dead (FR-3.4). */
    fun isIdleAt(now: Instant, idleTimeoutSeconds: Long): Boolean =
        now.isAfter(lastSeenAt.plusSeconds(idleTimeoutSeconds))

    fun isUsableAt(now: Instant, idleTimeoutSeconds: Long): Boolean =
        !isRevoked && !isExpiredAt(now) && !isIdleAt(now, idleTimeoutSeconds)

    fun revoke(reason: SessionRevocationReason, at: Instant = Instant.now()) {
        if (revokedAt == null) {
            revokedAt = at
            revokedReason = reason
        }
    }
}
