package com.factstore

import com.factstore.application.ApiKeyService
import com.factstore.application.UserService
import com.factstore.core.domain.OwnerType
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
 * Verifies that the filter correctly extracts and validates API keys from:
 * - `X-API-Key` header
 * - `Authorization: Bearer <key>` (standard)
 * - `Authorization: ApiKey <key>` (legacy scheme)
 *
 * Invalid / absent keys leave the request unauthenticated (routes are permitAll so still succeed).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApiKeyAuthFilterTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userService: UserService
    @Autowired lateinit var apiKeyService: ApiKeyService

    private fun createKeyForNewUser(ownerType: OwnerType = OwnerType.USER): String {
        val user = userService.createUser(
            CreateUserRequest(email = "filter-test-${System.nanoTime()}@example.com", name = "Filter User")
        )
        return apiKeyService.createApiKey(CreateApiKeyRequest(user.id, "Test Key", ownerType)).plainTextKey
    }

    @Test
    fun `request with valid X-API-Key header is authenticated`() {
        val key = createKeyForNewUser()
        mockMvc.get("/api/v1/flows") {
            header("X-API-Key", key)
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `request with valid Authorization Bearer header is authenticated`() {
        val key = createKeyForNewUser()
        mockMvc.get("/api/v1/flows") {
            header("Authorization", "Bearer $key")
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `request with valid Authorization ApiKey header is authenticated (legacy scheme)`() {
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
    fun `an invalid API key is refused, not treated as an anonymous request`() {
        // This used to assert the opposite: an invalid key left the context unauthenticated and
        // permitAll routes still succeeded, so a wrong key looked exactly like no key. #155
        // FR-2.3 makes the filter refuse it outright.
        mockMvc.get("/api/v1/flows") {
            header("X-API-Key", "fsp_invalidkeyxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx")
        }.andExpect {
            status { isUnauthorized() }
            header { string("WWW-Authenticate", "Bearer realm=\"factstore\"") }
        }
    }

    @Test
    fun `service account key is also accepted via X-API-Key header`() {
        val key = createKeyForNewUser(OwnerType.SERVICE_ACCOUNT)
        assertTrue(key.startsWith("fss_"), "Service account key should use 'fss_' prefix")
        mockMvc.get("/api/v1/flows") {
            header("X-API-Key", key)
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `service account key is accepted via Authorization Bearer header`() {
        val key = createKeyForNewUser(OwnerType.SERVICE_ACCOUNT)
        mockMvc.get("/api/v1/flows") {
            header("Authorization", "Bearer $key")
        }.andExpect {
            status { isOk() }
        }
    }
}
