package com.factstore

import com.factstore.application.ApiKeyService
import com.factstore.application.UserService
import com.factstore.core.domain.OwnerType
import com.factstore.dto.CreateApiKeyRequest
import com.factstore.dto.CreateUserRequest
import com.factstore.exception.NotFoundException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@Transactional
class ApiKeyServiceTest {

    @Autowired
    lateinit var apiKeyService: ApiKeyService

    @Autowired
    lateinit var userService: UserService

    private fun createUser(email: String = "apitest-${System.nanoTime()}@example.com"): UUID {
        return userService.createUser(CreateUserRequest(email = email, name = "API Test User")).id
    }

    @Test
    fun `create personal API key returns plain text key once`() {
        val userId = createUser()
        val resp = apiKeyService.createApiKey(CreateApiKeyRequest(userId, "CI Key", OwnerType.USER))

        assertTrue(resp.plainTextKey.startsWith("fsp_"), "User key should start with 'fsp_'")
        assertEquals(68, resp.plainTextKey.length, "Key should be 'fsp_' + 64 hex chars")
        assertEquals(OwnerType.USER, resp.ownerType)
        assertTrue(resp.isActive)
        assertEquals(userId, resp.ownerId)
    }

    @Test
    fun `create service account API key has correct prefix`() {
        val serviceAccountId = UUID.randomUUID()
        val resp = apiKeyService.createApiKey(CreateApiKeyRequest(serviceAccountId, "Deploy Bot", OwnerType.SERVICE_ACCOUNT))

        assertTrue(resp.plainTextKey.startsWith("fss_"), "Service account key should start with 'fss_'")
        assertEquals(OwnerType.SERVICE_ACCOUNT, resp.ownerType)
    }

    @Test
    fun `create API key for unknown user throws NotFoundException`() {
        assertThrows<NotFoundException> {
            apiKeyService.createApiKey(CreateApiKeyRequest(UUID.randomUUID(), "Key", OwnerType.USER))
        }
    }

    @Test
    fun `create API key for service account does not check user repository`() {
        // SERVICE_ACCOUNT owner type does not require a user to exist
        val serviceAccountId = UUID.randomUUID()
        val resp = apiKeyService.createApiKey(CreateApiKeyRequest(serviceAccountId, "SA Key", OwnerType.SERVICE_ACCOUNT))
        assertNotNull(resp)
        assertEquals(OwnerType.SERVICE_ACCOUNT, resp.ownerType)
    }

    @Test
    fun `plain text key prefix stored in database`() {
        val userId = createUser()
        val resp = apiKeyService.createApiKey(CreateApiKeyRequest(userId, "Prefix Test", OwnerType.USER))

        val stored = apiKeyService.listApiKeysForOwner(userId).first()
        assertEquals(resp.plainTextKey.take(12), stored.keyPrefix)
    }

    @Test
    fun `validateApiKey returns key response for valid key`() {
        val userId = createUser()
        val created = apiKeyService.createApiKey(CreateApiKeyRequest(userId, "Valid", OwnerType.USER))

        val validated = apiKeyService.validateApiKey(created.plainTextKey)
        assertNotNull(validated)
        assertEquals(created.id, validated!!.id)
        assertEquals(userId, validated.ownerId)
    }

    @Test
    fun `validateApiKey returns null for wrong key`() {
        val result = apiKeyService.validateApiKey("fsp_wrongkeyxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx")
        assertNull(result)
    }

    @Test
    fun `validateApiKey returns null for too-short key`() {
        val result = apiKeyService.validateApiKey("short")
        assertNull(result)
    }

    @Test
    fun `revokeApiKey deactivates the key`() {
        val userId = createUser()
        val created = apiKeyService.createApiKey(CreateApiKeyRequest(userId, "To Revoke", OwnerType.USER))

        apiKeyService.revokeApiKey(created.id)

        // After revocation, the key must no longer validate
        val validated = apiKeyService.validateApiKey(created.plainTextKey)
        assertNull(validated, "Revoked key should not validate")
    }

    @Test
    fun `revoke non-existent key throws NotFoundException`() {
        assertThrows<NotFoundException> {
            apiKeyService.revokeApiKey(UUID.randomUUID())
        }
    }

    @Test
    fun `listApiKeysForOwner returns keys for owner`() {
        val userId = createUser()
        apiKeyService.createApiKey(CreateApiKeyRequest(userId, "Key A", OwnerType.USER))
        apiKeyService.createApiKey(CreateApiKeyRequest(userId, "Key B", OwnerType.USER))

        val keys = apiKeyService.listApiKeysForOwner(userId)
        assertEquals(2, keys.size)
        assertTrue(keys.any { it.label == "Key A" })
        assertTrue(keys.any { it.label == "Key B" })
    }

    @Test
    fun `each generated key is unique`() {
        val userId = createUser()
        val key1 = apiKeyService.createApiKey(CreateApiKeyRequest(userId, "K1", OwnerType.USER)).plainTextKey
        val key2 = apiKeyService.createApiKey(CreateApiKeyRequest(userId, "K2", OwnerType.USER)).plainTextKey
        assertNotEquals(key1, key2)
    }

    @Test
    fun `api key with ttlDays has expiresAt set`() {
        val userId = createUser()
        val resp = apiKeyService.createApiKey(CreateApiKeyRequest(userId, "Short-lived", OwnerType.USER, ttlDays = 30))
        assertNotNull(resp.expiresAt)
        assertEquals(30, resp.ttlDays)
        assertTrue(resp.expiresAt!!.isAfter(resp.createdAt))
    }

    @Test
    fun `api key without ttlDays has null expiresAt`() {
        val userId = createUser()
        val resp = apiKeyService.createApiKey(CreateApiKeyRequest(userId, "No-expiry", OwnerType.USER))
        assertNull(resp.expiresAt)
        assertNull(resp.ttlDays)
    }
}
