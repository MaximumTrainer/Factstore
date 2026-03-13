package com.factstore.core.domain

import jakarta.persistence.*
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
    val expiresAt: Instant? = null
)
