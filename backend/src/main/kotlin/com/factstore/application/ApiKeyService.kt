package com.factstore.application

import com.factstore.core.domain.ApiKey
import com.factstore.core.domain.AuditEventType
import com.factstore.core.domain.AuthFailureReason
import com.factstore.core.domain.OwnerType
import com.factstore.core.domain.security.Permission
import com.factstore.core.port.inbound.IApiKeyService
import com.factstore.core.port.inbound.IAuditService
import com.factstore.core.port.outbound.IApiKeyRepository
import com.factstore.core.port.outbound.IUserRepository
import com.factstore.dto.ApiKeyCreatedResponse
import com.factstore.dto.ApiKeyPreset
import com.factstore.dto.ApiKeyResponse
import com.factstore.dto.CreateApiKeyRequest
import com.factstore.exception.BadRequestException
import com.factstore.exception.ForbiddenException
import com.factstore.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID

/**
 * API key lifecycle (#155 FR-3, FR-6).
 *
 * Every valid key used to grant the single authority `ROLE_API_USER`, so a CI key that only
 * needed to post attestations could delete flows, mint further keys, and read every
 * organisation's evidence. A key now carries scopes and, optionally, an organisation, and its
 * authorities are derived from those.
 */
@Service
@Transactional
class ApiKeyService(
    private val apiKeyRepository: IApiKeyRepository,
    private val userRepository: IUserRepository,
    private val passwordEncoder: BCryptPasswordEncoder,
    private val validationCache: ApiKeyValidationCache,
    private val auditService: IAuditService,
    private val actorResolver: ActorResolver,
    @Value("\${security.api-key.max-ttl-days:90}") private val maxTtlDays: Int,
    @Value("\${security.api-key.default-ttl-days:90}") private val defaultTtlDays: Int,
    @Value("\${security.api-key.allow-non-expiring:false}") private val allowNonExpiring: Boolean,
    @Value("\${security.api-key.rotation-overlap-hours:24}") private val rotationOverlapHours: Long,
    @Value("\${security.api-key.expiry-warning-days:7}") private val expiryWarningDays: Long
) : IApiKeyService {

    companion object {
        private const val SECONDS_PER_DAY = 86_400L
        private const val PREFIX_LENGTH = 12
    }

    private val log = LoggerFactory.getLogger(ApiKeyService::class.java)
    private val secureRandom = SecureRandom()

    /**
     * Generates a key, hashes it with BCrypt, persists the hash, and returns the plain text
     * exactly once.
     *
     * Key format: `fsp_<64 hex>` (user) or `fss_<64 hex>` (service account). The stored prefix
     * is the first 12 characters, used for an indexed lookup before the BCrypt comparison.
     */
    override fun createApiKey(request: CreateApiKeyRequest): ApiKeyCreatedResponse {
        if (request.ownerType == OwnerType.USER && !userRepository.existsById(request.ownerId)) {
            throw NotFoundException("User not found: ${request.ownerId}")
        }

        val scopes = resolveRequestedScopes(request)
        requireCallerHolds(scopes)
        val ttlDays = resolveTtl(request)

        val (plainTextKey, apiKey) = mint(
            ownerId = request.ownerId,
            ownerType = request.ownerType,
            label = request.label,
            scopes = scopes,
            orgSlug = request.orgSlug,
            ttlDays = ttlDays
        )

        val saved = apiKeyRepository.save(apiKey)
        audit(
            AuditEventType.API_KEY_CREATED, saved,
            mapOf("scopes" to scopes.map { it.scope }, "ttlDays" to ttlDays)
        )
        log.info(
            "Created API key: ${saved.id} ownerType=${saved.ownerType} prefix=${saved.keyPrefix} " +
                "scopes=${scopes.map { it.scope }} org=${saved.orgSlug}"
        )
        return saved.toCreatedResponse(plainTextKey, expiryWarningDays)
    }

    /**
     * Issues a replacement key and keeps the old one working for an overlap window, so a
     * pipeline can roll over without an outage (#155 FR-6.1).
     */
    override fun rotateApiKey(id: UUID, overlapHours: Long?): ApiKeyCreatedResponse {
        val existing = apiKeyRepository.findById(id) ?: throw NotFoundException("API key not found: $id")
        if (existing.supersededAt != null) {
            throw BadRequestException("API key $id has already been rotated")
        }
        requireCallerHolds(existing.scopes)

        val now = Instant.now()
        val overlap = (overlapHours ?: rotationOverlapHours).coerceAtLeast(0)

        val (plainTextKey, replacement) = mint(
            ownerId = existing.ownerId,
            ownerType = existing.ownerType,
            label = existing.label,
            scopes = existing.scopes,
            orgSlug = existing.orgSlug,
            ttlDays = existing.ttlDays ?: defaultTtlDays.takeIf { !allowNonExpiring }
        )
        replacement.rotatedFromId = existing.id
        val saved = apiKeyRepository.save(replacement)

        existing.supersededAt = now
        existing.overlapExpiresAt = now.plusSeconds(overlap * 3_600)
        apiKeyRepository.save(existing)
        // The old key's remaining life is short and bounded; drop any cached validation so the
        // new window is enforced from the next request.
        validationCache.invalidate(existing.id)

        audit(
            AuditEventType.API_KEY_ROTATED, saved,
            mapOf(
                "rotatedFromId" to existing.id.toString(),
                "previousKeyValidUntil" to existing.overlapExpiresAt.toString(),
                "overlapHours" to overlap
            )
        )
        log.info("Rotated API key ${existing.id} -> ${saved.id}, old key valid until ${existing.overlapExpiresAt}")
        return saved.toCreatedResponse(plainTextKey, expiryWarningDays)
    }

    @Transactional(readOnly = true)
    override fun listApiKeysForOwner(ownerId: UUID): List<ApiKeyResponse> =
        apiKeyRepository.findByOwnerId(ownerId).map { it.toResponse(expiryWarningDays) }

    override fun revokeApiKey(id: UUID) {
        val key = apiKeyRepository.findById(id) ?: throw NotFoundException("API key not found: $id")
        key.isActive = false
        apiKeyRepository.save(key)
        // Revocation must take effect on the next request, not after the cache TTL (FR-6.4).
        validationCache.invalidate(id)
        audit(AuditEventType.API_KEY_REVOKED, key, emptyMap())
        log.info("Revoked API key: $id")
    }

    /**
     * Validates a raw key.
     *
     * A cache hit skips the BCrypt comparison, which is tens of milliseconds of CPU per call
     * at the default cost factor (FR-9.2). The key row is still loaded and re-checked, so an
     * expiry or a rotation window closing is honoured even on a cache hit.
     */
    override fun validateApiKey(rawKey: String): ApiKeyResponse? = validate(rawKey).response

    /** As [validateApiKey], but says *why* a key was refused, for the 401 body (FR-2.4). */
    override fun validateWithReason(rawKey: String): ApiKeyValidation {
        return validate(rawKey)
    }

    private fun validate(rawKey: String): ApiKeyValidation {
        if (rawKey.length < PREFIX_LENGTH || !rawKey.startsWith("fs")) {
            return ApiKeyValidation(null, AuthFailureReason.MALFORMED)
        }
        val now = Instant.now()

        validationCache.get(rawKey)?.let { cachedId ->
            val cached = apiKeyRepository.findById(cachedId)
            if (cached != null) {
                cached.refusalReason(now)?.let { reason ->
                    validationCache.invalidate(cachedId)
                    return ApiKeyValidation(null, reason)
                }
                touch(cached, now)
                return ApiKeyValidation(cached.toResponse(expiryWarningDays), null)
            }
            validationCache.invalidate(cachedId)
        }

        val candidates = apiKeyRepository.findByKeyPrefix(rawKey.take(PREFIX_LENGTH))
        var refusal: AuthFailureReason? = null
        for (candidate in candidates) {
            // Compare the hash before deciding anything about state, so the work done is the
            // same whether or not the prefix matched a usable key (FR-9.4).
            if (!passwordEncoder.matches(rawKey, candidate.hashedKey)) continue

            val reason = candidate.refusalReason(now)
            if (reason != null) {
                refusal = reason
                continue
            }
            touch(candidate, now)
            validationCache.put(rawKey, candidate.id)
            return ApiKeyValidation(candidate.toResponse(expiryWarningDays), null)
        }
        return ApiKeyValidation(null, refusal ?: AuthFailureReason.UNKNOWN)
    }

    private fun touch(key: ApiKey, now: Instant) {
        key.lastUsedAt = now
        apiKeyRepository.save(key)
    }

    private fun mint(
        ownerId: UUID,
        ownerType: OwnerType,
        label: String,
        scopes: Set<Permission>,
        orgSlug: String?,
        ttlDays: Int?
    ): Pair<String, ApiKey> {
        val randomBytes = ByteArray(32)
        secureRandom.nextBytes(randomBytes)
        val randomHex = randomBytes.joinToString("") { "%02x".format(it) }

        val typePrefix = when (ownerType) {
            OwnerType.USER -> "fsp"
            OwnerType.SERVICE_ACCOUNT -> "fss"
        }
        val plainTextKey = "${typePrefix}_$randomHex"
        val expiresAt = ttlDays?.let { Instant.now().plusSeconds(it.toLong() * SECONDS_PER_DAY) }

        val apiKey = ApiKey(
            ownerId = ownerId,
            ownerType = ownerType,
            label = label,
            keyPrefix = plainTextKey.take(PREFIX_LENGTH),
            hashedKey = passwordEncoder.encode(plainTextKey),
            ttlDays = ttlDays,
            expiresAt = expiresAt,
            orgSlug = orgSlug
        )
        apiKey.scopes = scopes
        return plainTextKey to apiKey
    }

    /** Requested scopes, a preset, or the documented minimal set — never full access. */
    private fun resolveRequestedScopes(request: CreateApiKeyRequest): Set<Permission> {
        request.preset?.let { preset ->
            return when (preset) {
                ApiKeyPreset.CI_PIPELINE -> Permission.CI_PIPELINE_PRESET
                ApiKeyPreset.READ_ONLY -> Permission.DEFAULT_MINIMAL
            }
        }
        val requested = request.scopes ?: return Permission.DEFAULT_MINIMAL
        if (requested.isEmpty()) return Permission.DEFAULT_MINIMAL

        val parsed = Permission.parse(requested)
        if (parsed.unknown.isNotEmpty()) {
            throw BadRequestException(
                "Unknown scope(s): ${parsed.unknown.joinToString(", ")}. " +
                    "Valid scopes: ${Permission.entries.joinToString(", ") { it.scope }}"
            )
        }
        return parsed.permissions
    }

    /**
     * No privilege escalation by minting a key (#155 FR-3.4): a caller may only grant scopes
     * it holds itself. An unauthenticated caller — which is possible while enforcement is off —
     * is treated as holding nothing, so it can only create a minimal key.
     */
    private fun requireCallerHolds(requested: Set<Permission>) {
        val callerAuthorities = org.springframework.security.core.context.SecurityContextHolder
            .getContext().authentication
            ?.takeIf { it.isAuthenticated }
            ?.authorities
            ?.map { it.authority }
            ?.toSet()
            ?: return requireMinimalOnly(requested)

        // `admin` may grant anything; otherwise the caller must hold each scope requested.
        if (callerAuthorities.contains(Permission.ADMIN.authority)) return

        val missing = requested.filterNot { callerAuthorities.contains(it.authority) }
        if (missing.isNotEmpty()) {
            throw ForbiddenException(
                "Cannot grant scope(s) you do not hold: ${missing.joinToString(", ") { it.scope }}"
            )
        }
    }

    private fun requireMinimalOnly(requested: Set<Permission>) {
        val excess = requested - Permission.DEFAULT_MINIMAL
        if (excess.isNotEmpty()) {
            throw ForbiddenException(
                "An unauthenticated caller may only create a read-only key. Refused scope(s): " +
                    excess.joinToString(", ") { it.scope }
            )
        }
    }

    /**
     * A maximum TTL is enforced at creation, and a non-expiring key needs an explicit,
     * audited override (#155 FR-6.2).
     */
    private fun resolveTtl(request: CreateApiKeyRequest): Int? {
        if (request.neverExpires) {
            if (!allowNonExpiring) {
                throw BadRequestException(
                    "A key with no expiry requires security.api-key.allow-non-expiring=true. " +
                        "Set ttlDays instead; the maximum is $maxTtlDays days."
                )
            }
            log.warn(
                "Creating a non-expiring API key for owner ${request.ownerId} " +
                    "(label='${request.label}'); this is an explicit override"
            )
            return null
        }
        val requested = request.ttlDays ?: defaultTtlDays
        if (requested <= 0) throw BadRequestException("ttlDays must be positive")
        if (requested > maxTtlDays) {
            throw BadRequestException("ttlDays $requested exceeds the maximum of $maxTtlDays days")
        }
        return requested
    }

    private fun audit(eventType: AuditEventType, key: ApiKey, extra: Map<String, Any?>) {
        auditService.record(
            eventType = eventType,
            actor = actorResolver.current(),
            // The prefix identifies the key; the key itself never appears in an audit entry.
            payload = mapOf(
                "apiKeyId" to key.id.toString(),
                "keyPrefix" to key.keyPrefix,
                "ownerId" to key.ownerId.toString(),
                "ownerType" to key.ownerType.name,
                "orgSlug" to key.orgSlug
            ) + extra
        )
    }
}

/** The outcome of validating a credential: a principal, or the reason it was refused. */
data class ApiKeyValidation(val response: ApiKeyResponse?, val reason: AuthFailureReason?)

fun ApiKey.toResponse(expiryWarningDays: Long = 7): ApiKeyResponse {
    val now = Instant.now()
    val remaining = daysUntilExpiry(now)
    return ApiKeyResponse(
        id = id,
        ownerId = ownerId,
        ownerType = ownerType,
        label = label,
        scopes = scopes.map { it.scope }.sorted(),
        orgSlug = orgSlug,
        rotatedFromId = rotatedFromId,
        supersededAt = supersededAt,
        overlapExpiresAt = overlapExpiresAt,
        daysUntilExpiry = remaining,
        expiringSoon = remaining != null && remaining <= expiryWarningDays,
        keyPrefix = keyPrefix,
        isActive = isActive,
        createdAt = createdAt,
        lastUsedAt = lastUsedAt,
        ttlDays = ttlDays,
        expiresAt = expiresAt
    )
}

fun ApiKey.toCreatedResponse(plainTextKey: String, expiryWarningDays: Long = 7): ApiKeyCreatedResponse {
    val now = Instant.now()
    val remaining = daysUntilExpiry(now)
    return ApiKeyCreatedResponse(
        id = id,
        ownerId = ownerId,
        ownerType = ownerType,
        label = label,
        scopes = scopes.map { it.scope }.sorted(),
        orgSlug = orgSlug,
        rotatedFromId = rotatedFromId,
        supersededAt = supersededAt,
        overlapExpiresAt = overlapExpiresAt,
        daysUntilExpiry = remaining,
        expiringSoon = remaining != null && remaining <= expiryWarningDays,
        keyPrefix = keyPrefix,
        isActive = isActive,
        createdAt = createdAt,
        lastUsedAt = lastUsedAt,
        ttlDays = ttlDays,
        expiresAt = expiresAt,
        plainTextKey = plainTextKey
    )
}
