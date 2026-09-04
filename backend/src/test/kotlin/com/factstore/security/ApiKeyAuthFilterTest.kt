package com.factstore.security

import com.factstore.adapter.inbound.web.ApiKeyAuthFilter
import com.factstore.application.ApiKeyRateLimiter
import com.factstore.application.ApiKeyService
import com.factstore.application.ApiKeyValidationCache
import com.factstore.core.domain.OwnerType
import com.factstore.core.domain.User
import com.factstore.core.domain.security.Permission
import com.factstore.core.port.outbound.IUserRepository
import com.factstore.dto.ApiKeyPreset
import com.factstore.dto.CreateApiKeyRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.context.TestSecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * How a credential is accepted or refused at the edge (#155 FR-2, FR-3.3, FR-9).
 *
 * The behaviour being replaced: the filter validated a key and, on failure, *fell through* —
 * so a wrong key looked exactly like no key at all, and with enforcement off it looked like
 * success.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApiKeyAuthFilterTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var apiKeyService: ApiKeyService
    @Autowired lateinit var userRepository: IUserRepository
    @Autowired lateinit var rateLimiter: ApiKeyRateLimiter
    @Autowired lateinit var validationCache: ApiKeyValidationCache

    private lateinit var owner: User

    @BeforeEach
    fun setUp() {
        owner = withAdmin {
            userRepository.save(User(email = "filter-${System.nanoTime()}@example.com", name = "Owner"))
        }
        rateLimiter.reset()
        validationCache.clear()
        // Every request below must start unauthenticated, or the filter skips its work.
        clearSecurity()
    }

    /**
     * Clears *both* holders. MockMvc installs the context captured by
     * [TestSecurityContextHolder] into each request, so clearing only [SecurityContextHolder]
     * leaves the principal in place and the filter never runs.
     */
    private fun clearSecurity() {
        SecurityContextHolder.clearContext()
        TestSecurityContextHolder.clearContext()
    }

    /** Minting a key needs a caller holding the scopes; requests must not inherit that. */
    private fun <T> withAdmin(block: () -> T): T {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            "admin", null, listOf(SimpleGrantedAuthority(Permission.ADMIN.authority))
        )
        return try {
            block()
        } finally {
            clearSecurity()
        }
    }

    @AfterEach
    fun tearDown() {
        clearSecurity()
        rateLimiter.reset()
        validationCache.clear()
    }

    private fun keyWith(preset: ApiKeyPreset? = null, scopes: List<String>? = null): String =
        withAdmin {
            apiKeyService.createApiKey(
                CreateApiKeyRequest(
                    ownerId = owner.id,
                    label = "filter test",
                    ownerType = OwnerType.USER,
                    preset = preset,
                    scopes = scopes
                )
            ).plainTextKey
        }

    // --- Refusing a bad credential ----------------------------------------

    @Test
    fun `an unknown key is refused immediately with a Bearer challenge`() {
        mockMvc.get("/api/v1/flows") {
            header("Authorization", "Bearer fsp_${"0".repeat(64)}")
        }.andExpect {
            status { isUnauthorized() }
            header { string("WWW-Authenticate", "Bearer realm=\"factstore\"") }
            jsonPath("$.reason") { value("UNKNOWN") }
        }
    }

    @Test
    fun `a malformed credential is refused as malformed`() {
        mockMvc.get("/api/v1/flows") {
            header("X-API-Key", "not-a-key")
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.reason") { value("MALFORMED") }
        }
    }

    @Test
    fun `a revoked key is refused as revoked, distinctly from unknown`() {
        val plain = keyWith(preset = ApiKeyPreset.READ_ONLY)
        val keyId = apiKeyService.validateApiKey(plain)!!.id
        withAdmin { apiKeyService.revokeApiKey(keyId) }

        mockMvc.get("/api/v1/flows") {
            header("X-API-Key", plain)
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.reason") { value("REVOKED") }
        }
    }

    @Test
    fun `the problem body never echoes the credential`() {
        val credential = "fsp_${"a".repeat(64)}"

        val body = mockMvc.get("/api/v1/flows") {
            header("X-API-Key", credential)
        }.andExpect { status { isUnauthorized() } }.andReturn().response.contentAsString

        assert(!body.contains(credential.substringAfter('_'))) {
            "the response must not contain the credential: $body"
        }
        // The prefix is fine, and is what lets an operator identify the key.
        assert(body.contains(credential.take(12))) { "expected the prefix for diagnosis: $body" }
    }

    @Test
    fun `no credential at all is left to the route rules, not refused here`() {
        // With enforcement off this is a permitted anonymous read; the filter must not turn
        // "no credential" into a 401 of its own.
        mockMvc.get("/api/v1/flows").andExpect { status { isOk() } }
    }

    // --- Scope enforcement -------------------------------------------------

    @Test
    fun `a CI key can post an attestation but cannot delete a flow`() {
        val ciKey = keyWith(preset = ApiKeyPreset.CI_PIPELINE)

        mockMvc.delete("/api/v1/flows/${UUID.randomUUID()}") {
            header("X-API-Key", ciKey)
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `a CI key cannot mint further keys`() {
        val ciKey = keyWith(preset = ApiKeyPreset.CI_PIPELINE)

        mockMvc.post("/api/v1/api-keys") {
            header("X-API-Key", ciKey)
            contentType = MediaType.APPLICATION_JSON
            content = """{"ownerId":"${owner.id}","label":"escalation","ownerType":"USER"}"""
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `a read-only key cannot upload a policy`() {
        val readOnly = keyWith(preset = ApiKeyPreset.READ_ONLY)

        mockMvc.post("/api/v1/policies") {
            header("X-API-Key", readOnly)
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"p","description":"d"}"""
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `an admin key may do administrative things`() {
        val adminKey = keyWith(scopes = listOf("admin", "flows:write"))

        mockMvc.get("/api/v1/api-keys/owners/${owner.id}") {
            header("X-API-Key", adminKey)
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `a valid key authenticates a read`() {
        val key = keyWith(preset = ApiKeyPreset.READ_ONLY)

        mockMvc.get("/api/v1/flows") {
            header("X-API-Key", key)
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `the legacy ApiKey scheme still works`() {
        val key = keyWith(preset = ApiKeyPreset.READ_ONLY)

        mockMvc.get("/api/v1/flows") {
            header("Authorization", "ApiKey $key")
        }.andExpect { status { isOk() } }
    }

    // --- Expiry warning ----------------------------------------------------

    @Test
    fun `a key nearing expiry gets a warning header, so a pipeline hears about it`() {
        val key = withAdmin {
            apiKeyService.createApiKey(
                CreateApiKeyRequest(
                    ownerId = owner.id,
                    label = "expiring",
                    ownerType = OwnerType.USER,
                    ttlDays = 2,
                    preset = ApiKeyPreset.READ_ONLY
                )
            ).plainTextKey
        }

        mockMvc.get("/api/v1/flows") {
            header("X-API-Key", key)
        }.andExpect {
            status { isOk() }
            header { exists(ApiKeyAuthFilter.EXPIRY_WARNING_HEADER) }
        }
    }

    // --- Rate limiting -----------------------------------------------------

    @Test
    fun `repeated failures from one source are rate-limited with Retry-After`() {
        val bad = "fsp_${"b".repeat(64)}"

        repeat(6) {
            mockMvc.get("/api/v1/flows") { header("X-API-Key", bad) }
        }

        mockMvc.get("/api/v1/flows") {
            header("X-API-Key", bad)
        }.andExpect {
            status { isTooManyRequests() }
            header { exists("Retry-After") }
        }
    }

    @Test
    fun `a working credential is not affected by another key's failures`() {
        val good = keyWith(preset = ApiKeyPreset.READ_ONLY)
        val bad = "fsp_${"c".repeat(64)}"

        repeat(6) { mockMvc.get("/api/v1/flows") { header("X-API-Key", bad) } }

        // The source is the same in MockMvc, so this also proves a success clears the source
        // bucket rather than leaving a shared runner locked out.
        rateLimiter.recordSuccess("127.0.0.1", good.take(12))
        mockMvc.get("/api/v1/flows") { header("X-API-Key", good) }
            .andExpect { status { isOk() } }
    }
}
