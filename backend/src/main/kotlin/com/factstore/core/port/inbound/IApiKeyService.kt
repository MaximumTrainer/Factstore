package com.factstore.core.port.inbound

import com.factstore.dto.ApiKeyCreatedResponse
import com.factstore.dto.ApiKeyResponse
import com.factstore.dto.CreateApiKeyRequest
import java.util.UUID

interface IApiKeyService {
    /** Generates a new API key, hashes it, stores it, and returns the plain-text key once. */
    fun createApiKey(request: CreateApiKeyRequest): ApiKeyCreatedResponse
    fun listApiKeysForOwner(ownerId: UUID): List<ApiKeyResponse>
    fun revokeApiKey(id: UUID)

    /**
     * Issues a replacement key, keeping the old one valid for an overlap window so a pipeline
     * can roll over without an outage (#155 FR-6.1).
     */
    fun rotateApiKey(id: UUID, overlapHours: Long? = null): ApiKeyCreatedResponse

    /** As [validateApiKey], but reports why a credential was refused (#155 FR-2.4). */
    fun validateWithReason(rawKey: String): com.factstore.application.ApiKeyValidation
    /**
     * Validates an incoming raw API key against stored hashed keys.
     * Returns the matching [ApiKeyResponse] (and updates lastUsedAt) or null if invalid.
     */
    fun validateApiKey(rawKey: String): ApiKeyResponse?
}
