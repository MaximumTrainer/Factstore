package com.factstore.application

import com.factstore.core.domain.ApiKey
import com.factstore.core.domain.OwnerType
import com.factstore.core.port.inbound.IApiKeyService
import com.factstore.core.port.outbound.IApiKeyRepository
import com.factstore.core.port.outbound.IUserRepository
import com.factstore.dto.ApiKeyCreatedResponse
import com.factstore.dto.ApiKeyResponse
import com.factstore.dto.CreateApiKeyRequest
import com.factstore.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class ApiKeyService(
    private val apiKeyRepository: IApiKeyRepository,
    private val userRepository: IUserRepository,
    private val passwordEncoder: BCryptPasswordEncoder
) : IApiKeyService {

    companion object {
        private const val SECONDS_PER_DAY = 86_400L
    }

    private val log = LoggerFactory.getLogger(ApiKeyService::class.java)
    private val secureRandom = SecureRandom()

    /**
     * Generates a cryptographically secure API key, hashes it with BCrypt, persists the
     * hash, and returns the plain-text key exactly once to the caller.
     *
     * Key format: `fsp_<64 hex chars>` (user/personal) or `fss_<64 hex chars>` (service account).
     * The prefix stored in the database is the first 12 characters of the full key,
     * which is used for an efficient indexed lookup before the BCrypt comparison.
     */
    override fun createApiKey(request: CreateApiKeyRequest): ApiKeyCreatedResponse {
        if (request.ownerType == OwnerType.USER && !userRepository.existsById(request.ownerId)) {
            throw NotFoundException("User not found: ${request.ownerId}")
        }

        val randomBytes = ByteArray(32)
        secureRandom.nextBytes(randomBytes)
        val randomHex = randomBytes.joinToString("") { "%02x".format(it) }

        val typePrefix = when (request.ownerType) {
            OwnerType.USER -> "fsp"
            OwnerType.SERVICE_ACCOUNT -> "fss"
        }
        val plainTextKey = "${typePrefix}_$randomHex"
        val keyPrefix = plainTextKey.take(12)
        val hashedKey = passwordEncoder.encode(plainTextKey)

        val expiresAt = request.ttlDays?.let { days ->
            Instant.now().plusSeconds(days.toLong() * SECONDS_PER_DAY)
        }

        val apiKey = ApiKey(
            ownerId = request.ownerId,
            ownerType = request.ownerType,
            label = request.label,
            keyPrefix = keyPrefix,
            hashedKey = hashedKey,
            ttlDays = request.ttlDays,
            expiresAt = expiresAt
        )
        val saved = apiKeyRepository.save(apiKey)
        log.info("Created API key: ${saved.id} ownerType=${saved.ownerType} prefix=${saved.keyPrefix}")

        return saved.toCreatedResponse(plainTextKey)
    }

    @Transactional(readOnly = true)
    override fun listApiKeysForOwner(ownerId: UUID): List<ApiKeyResponse> {
        return apiKeyRepository.findByOwnerId(ownerId).map { it.toResponse() }
    }

    override fun revokeApiKey(id: UUID) {
        val key = apiKeyRepository.findById(id) ?: throw NotFoundException("API key not found: $id")
        key.isActive = false
        apiKeyRepository.save(key)
        log.info("Revoked API key: $id")
    }

    /**
     * Validates a raw key from an incoming request:
     * 1. Extracts the prefix (first 12 chars) for indexed database lookup.
     * 2. Iterates matching active keys and verifies the BCrypt hash.
     * 3. Rejects expired keys (when [ApiKey.expiresAt] is set and in the past).
     * 4. Updates [ApiKey.lastUsedAt] on success.
     */
    override fun validateApiKey(rawKey: String): ApiKeyResponse? {
        if (rawKey.length < 12) return null
        val prefix = rawKey.take(12)
        val now = Instant.now()
        val candidates = apiKeyRepository.findByKeyPrefix(prefix).filter { key ->
            key.isActive && (key.expiresAt == null || key.expiresAt.isAfter(now))
        }
        for (candidate in candidates) {
            if (passwordEncoder.matches(rawKey, candidate.hashedKey)) {
                candidate.lastUsedAt = now
                apiKeyRepository.save(candidate)
                return candidate.toResponse()
            }
        }
        return null
    }
}

fun ApiKey.toResponse() = ApiKeyResponse(
    id = id,
    ownerId = ownerId,
    ownerType = ownerType,
    label = label,
    keyPrefix = keyPrefix,
    isActive = isActive,
    createdAt = createdAt,
    lastUsedAt = lastUsedAt,
    ttlDays = ttlDays,
    expiresAt = expiresAt
)

fun ApiKey.toCreatedResponse(plainTextKey: String) = ApiKeyCreatedResponse(
    id = id,
    ownerId = ownerId,
    ownerType = ownerType,
    label = label,
    keyPrefix = keyPrefix,
    isActive = isActive,
    createdAt = createdAt,
    lastUsedAt = lastUsedAt,
    ttlDays = ttlDays,
    expiresAt = expiresAt,
    plainTextKey = plainTextKey
)
