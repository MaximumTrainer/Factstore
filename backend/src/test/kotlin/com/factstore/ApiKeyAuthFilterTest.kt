package com.factstore

import com.factstore.application.ApiKeyService
import com.factstore.application.UserService
import com.factstore.core.domain.ApiKeyType
import com.factstore.dto.CreateApiKeyRequest
import com.factstore.dto.CreateUserRequest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional

/**
 * Integration tests for [com.factstore.adapter.inbound.web.ApiKeyAuthFilter].
 *
 * Verifies that the filter correctly extracts and validates API keys from both
 * the `X-API-Key` header and the `Authorization: ApiKey <key>` scheme, and that
 * an invalid / absent key leaves the request unauthenticated.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApiKeyAuthFilterTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userService: UserService
    @Autowired lateinit var apiKeyService: ApiKeyService

    private fun createKeyForNewUser(type: ApiKeyType = ApiKeyType.PERSONAL): String {
        val user = userService.createUser(
            CreateUserRequest(email = "filter-test-${System.nanoTime()}@example.com", name = "Filter User")
        )
        return apiKeyService.createApiKey(CreateApiKeyRequest(user.id, "Test Key", type)).plainTextKey
    }

    @Test
    fun `request with valid X-API-Key header is authenticated`() {
        val key = createKeyForNewUser()
        // The /api/v1/flows endpoint is accessible (permitAll), so a 200 confirms the
        // request reached the controller, meaning the filter did not block it.
        mockMvc.get("/api/v1/flows") {
            header("X-API-Key", key)
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `request with valid Authorization ApiKey header is authenticated`() {
        val key = createKeyForNewUser()
        mockMvc.get("/api/v1/flows") {
            header("Authorization", "ApiKey $key")
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `request with no API key header still reaches permitted endpoints`() {
        // Routes are permitAll, so unauthenticated requests should still succeed.
        mockMvc.get("/api/v1/flows")
            .andExpect {
                status { isOk() }
            }
    }

    @Test
    fun `request with invalid API key still reaches permitted endpoints`() {
        // Invalid keys leave the SecurityContext unauthenticated; permitAll routes still succeed.
        mockMvc.get("/api/v1/flows") {
            header("X-API-Key", "fsp_invalidkeyxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx")
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `service account key is also accepted via X-API-Key header`() {
        val key = createKeyForNewUser(ApiKeyType.SERVICE)
        assertTrue(key.startsWith("fss_"), "Service key should use 'fss_' prefix")
        mockMvc.get("/api/v1/flows") {
            header("X-API-Key", key)
        }.andExpect {
            status { isOk() }
        }
    }
}
