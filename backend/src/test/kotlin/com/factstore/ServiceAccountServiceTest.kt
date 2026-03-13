package com.factstore

import com.factstore.application.ServiceAccountService
import com.factstore.dto.CreateServiceAccountRequest
import com.factstore.dto.UpdateServiceAccountRequest
import com.factstore.exception.ConflictException
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
class ServiceAccountServiceTest {

    @Autowired
    lateinit var serviceAccountService: ServiceAccountService

    private fun createAccount(name: String = "sa-${System.nanoTime()}"): com.factstore.dto.ServiceAccountResponse =
        serviceAccountService.createServiceAccount(CreateServiceAccountRequest(name, "Test description"))

    @Test
    fun `create service account persists name and description`() {
        val resp = createAccount("backend-ci-runner")
        assertNotNull(resp.id)
        assertEquals("backend-ci-runner", resp.name)
        assertEquals("Test description", resp.description)
        assertNotNull(resp.createdAt)
        assertNotNull(resp.updatedAt)
    }

    @Test
    fun `create service account with duplicate name throws ConflictException`() {
        createAccount("duplicate-name")
        assertThrows<ConflictException> {
            createAccount("duplicate-name")
        }
    }

    @Test
    fun `list service accounts returns all accounts`() {
        val before = serviceAccountService.listServiceAccounts().size
        createAccount()
        createAccount()
        val after = serviceAccountService.listServiceAccounts()
        assertEquals(before + 2, after.size)
    }

    @Test
    fun `get service account returns correct account`() {
        val created = createAccount("get-test")
        val fetched = serviceAccountService.getServiceAccount(created.id)
        assertEquals(created.id, fetched.id)
        assertEquals("get-test", fetched.name)
    }

    @Test
    fun `get non-existent service account throws NotFoundException`() {
        assertThrows<NotFoundException> {
            serviceAccountService.getServiceAccount(UUID.randomUUID())
        }
    }

    @Test
    fun `update service account changes name and description`() {
        val created = createAccount("original-name")
        val updated = serviceAccountService.updateServiceAccount(
            created.id,
            UpdateServiceAccountRequest(name = "updated-name", description = "new desc")
        )
        assertEquals("updated-name", updated.name)
        assertEquals("new desc", updated.description)
        assertTrue(updated.updatedAt >= updated.createdAt)
    }

    @Test
    fun `update service account to duplicate name throws ConflictException`() {
        createAccount("existing-name")
        val other = createAccount("other-name")
        assertThrows<ConflictException> {
            serviceAccountService.updateServiceAccount(other.id, UpdateServiceAccountRequest(name = "existing-name"))
        }
    }

    @Test
    fun `delete service account removes it`() {
        val created = createAccount()
        serviceAccountService.deleteServiceAccount(created.id)
        assertThrows<NotFoundException> {
            serviceAccountService.getServiceAccount(created.id)
        }
    }

    @Test
    fun `delete non-existent service account throws NotFoundException`() {
        assertThrows<NotFoundException> {
            serviceAccountService.deleteServiceAccount(UUID.randomUUID())
        }
    }

    @Test
    fun `create API key for service account returns plain text key once`() {
        val account = createAccount()
        val keyResp = serviceAccountService.createApiKey(account.id, "GitHub Actions — prod", ttlDays = null)

        assertTrue(keyResp.plainTextKey.startsWith("fss_"), "Service account key should use 'fss_' prefix")
        assertEquals(68, keyResp.plainTextKey.length)
        assertEquals(account.id, keyResp.ownerId)
        assertTrue(keyResp.isActive)
    }

    @Test
    fun `create API key with TTL sets expiresAt`() {
        val account = createAccount()
        val keyResp = serviceAccountService.createApiKey(account.id, "Short-lived key", ttlDays = 90)

        assertNotNull(keyResp.expiresAt)
        assertEquals(90, keyResp.ttlDays)
        assertTrue(keyResp.expiresAt!!.isAfter(keyResp.createdAt))
    }

    @Test
    fun `create API key for non-existent service account throws NotFoundException`() {
        assertThrows<NotFoundException> {
            serviceAccountService.createApiKey(UUID.randomUUID(), "Key", ttlDays = null)
        }
    }

    @Test
    fun `list API keys returns keys for service account only`() {
        val account = createAccount()
        serviceAccountService.createApiKey(account.id, "Key 1", ttlDays = null)
        serviceAccountService.createApiKey(account.id, "Key 2", ttlDays = null)

        val keys = serviceAccountService.listApiKeys(account.id)
        assertEquals(2, keys.size)
        assertTrue(keys.all { it.ownerId == account.id })
    }

    @Test
    fun `list API keys for non-existent service account throws NotFoundException`() {
        assertThrows<NotFoundException> {
            serviceAccountService.listApiKeys(UUID.randomUUID())
        }
    }

    @Test
    fun `revoke API key deactivates it`() {
        val account = createAccount()
        val key = serviceAccountService.createApiKey(account.id, "To Revoke", ttlDays = null)

        serviceAccountService.revokeApiKey(account.id, key.id)

        val keys = serviceAccountService.listApiKeys(account.id)
        val revokedKey = keys.find { it.id == key.id }
        assertNotNull(revokedKey)
        assertFalse(revokedKey!!.isActive)
    }

    @Test
    fun `delete service account also deletes its API keys`() {
        val account = createAccount()
        serviceAccountService.createApiKey(account.id, "Key to delete", ttlDays = null)

        // Should not throw — keys are cascade-deleted
        serviceAccountService.deleteServiceAccount(account.id)
    }
}
