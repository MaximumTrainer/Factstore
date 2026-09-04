package com.factstore.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.factstore.application.ApiKeyRateLimiter
import com.factstore.application.ApiKeyService
import com.factstore.application.ApiKeyValidationCache
import com.factstore.core.domain.OwnerType
import com.factstore.core.domain.User
import com.factstore.core.domain.security.Permission
import com.factstore.core.domain.security.RoleModel
import com.factstore.core.port.outbound.IUserRepository
import org.junit.jupiter.api.Assertions.assertTrue
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
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional

/**
 * What the bootstrap admin credential can actually do (#155 FR-7).
 *
 * The defect this pins down: `admin` was treated as one narrow scope covering key management,
 * so the bootstrap credential — the *only* credential a fresh enforced deployment has — could
 * not create a flow. `POST /api/v1/flows` requires `SCOPE_flows:write`, and an `admin`-scoped
 * key held `SCOPE_admin` and nothing else, so it got a `403`.
 *
 * That made first-run unusable: the operator is handed a credential and the first thing they
 * try with it fails. It broke `dogfood.yml` and `verify-factstore.yml` on `main` for exactly
 * this reason, and neither had a test that would have caught it because both used
 * `@WithMockUser`, which grants authorities directly and never exercises scope derivation.
 *
 * `admin` is not a *narrower* privilege than the rest — a caller holding it may already mint a
 * key carrying any scope at all. Expanding it to the permissions it can trivially grant itself
 * is honest about what it is, rather than a widening.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BootstrapCredentialCapabilityTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var apiKeyService: ApiKeyService
    @Autowired lateinit var userRepository: IUserRepository
    @Autowired lateinit var rateLimiter: ApiKeyRateLimiter
    @Autowired lateinit var validationCache: ApiKeyValidationCache
    @Autowired lateinit var jsonMapper: ObjectMapper

    /** The value `dogfood.yml` and `verify-factstore.yml` seed into their instances. */
    private val bootstrapKey = "fsp_" + "c1".repeat(32)

    @BeforeEach
    fun setUp() {
        rateLimiter.reset()
        validationCache.clear()
        val owner = withAdmin {
            userRepository.save(
                User(email = "bootstrap-${System.nanoTime()}@example.com", name = "Bootstrap")
            )
        }
        withAdmin {
            apiKeyService.seedApiKey(
                ownerId = owner.id,
                ownerType = OwnerType.USER,
                label = "bootstrap admin (from configuration)",
                scopes = setOf(Permission.ADMIN),
                plainTextKey = bootstrapKey,
                ttlDays = 7
            )
        }
        clearSecurity()
    }

    private fun clearSecurity() {
        SecurityContextHolder.clearContext()
        TestSecurityContextHolder.clearContext()
    }

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

    @Test
    fun `the bootstrap credential can create a flow, which is the first thing CI does with it`() {
        mockMvc.post("/api/v1/flows") {
            header("X-API-Key", bootstrapKey)
            contentType = MediaType.APPLICATION_JSON
            content = """
                {"name":"bootstrap-flow-${System.nanoTime()}",
                 "description":"created with the bootstrap credential",
                 "requiredAttestationTypes":["backend-tests"]}
            """.trimIndent()
        }.andExpect {
            status { isCreated() }
        }
    }

    @Test
    fun `the bootstrap credential can create a trail, the step that failed on main`() {
        val flow = mockMvc.post("/api/v1/flows") {
            header("X-API-Key", bootstrapKey)
            contentType = MediaType.APPLICATION_JSON
            content = """
                {"name":"bootstrap-trail-flow-${System.nanoTime()}",
                 "description":"for a trail",
                 "requiredAttestationTypes":["backend-tests"]}
            """.trimIndent()
        }.andReturn().response.contentAsString

        val flowId = jsonMapper.readTree(flow).get("id").asText()

        mockMvc.post("/api/v1/trails") {
            header("X-API-Key", bootstrapKey)
            contentType = MediaType.APPLICATION_JSON
            content = """
                {"flowId":"$flowId","gitCommitSha":"abc1234",
                 "gitBranch":"main","gitAuthor":"ci",
                 "gitAuthorEmail":"ci@example.com"}
            """.trimIndent()
        }.andExpect {
            status { isCreated() }
        }
    }

    @Test
    fun `an admin scope grants every permission, matching what the ADMIN role means`() {
        val authorities = RoleModel.authoritiesForScopes(setOf(Permission.ADMIN))

        // The same word must not mean "everything" for a user and "key management only" for a
        // key: that inconsistency is what made the bootstrap credential unusable.
        Permission.entries.forEach { permission ->
            assertTrue(
                authorities.contains(permission.authority),
                "an admin-scoped credential should hold ${permission.scope}"
            )
        }
    }

    @Test
    fun `a non-admin scope set is still exactly what was granted`() {
        val authorities = RoleModel.authoritiesForScopes(setOf(Permission.ATTESTATIONS_WRITE))

        assertTrue(authorities.contains(Permission.ATTESTATIONS_WRITE.authority))
        assertTrue(
            !authorities.contains(Permission.FLOWS_WRITE.authority),
            "expanding admin must not expand anything else"
        )
    }
}
