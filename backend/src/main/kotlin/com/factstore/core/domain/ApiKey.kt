package com.factstore.core.domain

import jakarta.persistence.*
import com.factstore.core.domain.security.Permission
import java.time.Instant
import java.util.UUID

enum class OwnerType { USER, SERVICE_ACCOUNT }

@Entity
@Table(name = "api_keys")
class ApiKey(
    @Id
    val id: UUID = UUID.randomUUID(),

    /**
     * UUID of the owner — either a User or a ServiceAccount, depending on [ownerType].
     * No database foreign-key constraint is applied so that the same column can
     * reference two different tables polymorphically.
     */
    @Column(name = "owner_id", nullable = false)
    val ownerId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false)
    val ownerType: OwnerType,

    /** Human-readable label for this key (e.g. "GitHub Actions — prod"). */
    @Column(nullable = false)
    var label: String,

    /**
     * Stores the first 12 characters of the generated key (including type prefix).
     * Used for efficient database lookup before BCrypt verification.
     * Example: "fsp_abcde12" (personal) or "fss_abcde12" (service)
     */
    @Column(name = "key_prefix", nullable = false, length = 12)
    val keyPrefix: String,

    /**
     * BCrypt hash of the full API key. Never store the plain-text key.
     */
    @Column(name = "hashed_key", nullable = false)
    var hashedKey: String,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "last_used_at")
    var lastUsedAt: Instant? = null,

    /** Optional TTL in days. Null means the key never expires. */
    @Column(name = "ttl_days")
    val ttlDays: Int? = null,

    /**
     * Pre-computed expiry timestamp (createdAt + ttlDays).
     * Null when [ttlDays] is null (no expiry).
     */
    @Column(name = "expires_at")
    val expiresAt: Instant? = null,

    /**
     * Granted scopes as a comma-separated `resource:action` list (#155 FR-3).
     *
     * Stored as a string rather than a join table: the vocabulary is a fixed enum
     * ([com.factstore.core.domain.security.Permission]) validated on the way in, so a table
     * would add a migration and a join without making anything more correct. Use [scopes].
     */
    @Column(name = "scopes", columnDefinition = "TEXT")
    var scopesRaw: String? = null,

    /**
     * The organisation this key authenticates into (#155 FR-5.1).
     *
     * Null means unbound — the single-tenant case, and the state of every key that existed
     * before scopes were introduced.
     */
    @Column(name = "org_slug", nullable = true, length = 255)
    var orgSlug: String? = null,

    /** The key this one replaced, when it was created by rotation (#155 FR-6.1). */
    @Column(name = "rotated_from_id", nullable = true)
    var rotatedFromId: UUID? = null,

    /** When this key was replaced by a rotation. */
    @Column(name = "superseded_at", nullable = true)
    var supersededAt: Instant? = null,

    /**
     * A superseded key stops working at this instant, not immediately, so a pipeline holding
     * the old value can roll over without an outage.
     */
    @Column(name = "overlap_expires_at", nullable = true)
    var overlapExpiresAt: Instant? = null
) {
    /** The granted permissions. Empty means the key can do nothing but authenticate. */
    var scopes: Set<Permission>
        get() = scopesRaw
            ?.split(",")
            ?.mapNotNull { Permission.fromScope(it.trim()) }
            ?.toSet()
            ?: emptySet()
        set(value) {
            scopesRaw = value.joinToString(",") { it.scope }
        }

    /** True when the key is currently accepted. */
    fun isUsableAt(now: Instant): Boolean {
        if (!isActive) return false
        if (expiresAt != null && !expiresAt.isAfter(now)) return false
        // A superseded key lives until its overlap window closes.
        if (supersededAt != null && (overlapExpiresAt == null || !overlapExpiresAt!!.isAfter(now))) return false
        return true
    }

    /** Why the key was refused, for the 401 problem body (#155 FR-2.4). */
    fun refusalReason(now: Instant): AuthFailureReason? = when {
        !isActive -> AuthFailureReason.REVOKED
        expiresAt != null && !expiresAt.isAfter(now) -> AuthFailureReason.EXPIRED
        supersededAt != null && (overlapExpiresAt == null || !overlapExpiresAt!!.isAfter(now)) ->
            AuthFailureReason.EXPIRED
        else -> null
    }

    /** Days until expiry, for the nearing-expiry warning (#155 FR-6.3). Null when no TTL. */
    fun daysUntilExpiry(now: Instant): Long? = expiresAt?.let {
        java.time.Duration.between(now, it).toDays()
    }
}

/**
 * Why a credential was refused. Distinguishes the cases a caller can act on without
 * revealing whether a given key exists (#155 FR-2.4).
 */
enum class AuthFailureReason { MISSING, MALFORMED, UNKNOWN, EXPIRED, REVOKED, INSUFFICIENT_SCOPE, WRONG_ORGANISATION }
