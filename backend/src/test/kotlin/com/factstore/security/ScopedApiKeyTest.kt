package com.factstore.security

import com.factstore.application.ApiKeyService
import com.factstore.application.ApiKeyValidationCache
import com.factstore.core.domain.AuditEventType
import com.factstore.core.domain.AuthFailureReason
import com.factstore.core.domain.OwnerType
import com.factstore.core.domain.User
import com.factstore.core.domain.security.Permission
import com.factstore.core.port.inbound.IAuditService
import com.factstore.core.port.outbound.IApiKeyRepository
import com.factstore.core.port.outbound.IUserRepository
import com.factstore.dto.ApiKeyPreset
import com.factstore.dto.CreateApiKeyRequest
import com.factstore.exception.BadRequestException
import com.factstore.exception.ForbiddenException
import com.factstore.exception.NotFoundException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Scoped API keys, rotation and TTL (#155 FR-3, FR-6).
 *
 * The behaviour being replaced: every valid key granted the single authority
 * `ROLE_API_USER`, so a CI key that only needed to post attestations could delete flows,
 * mint further keys, and read every organisation's evidence.
 */
@SpringBootTest
@Transactional
class ScopedApiKeyTest {

    @Autowired lateinit var apiKeyService: ApiKeyService
    @Autowired lateinit var apiKeyRepository: IApiKeyRepository
    @Autowired lateinit var userRepository: IUserRepository
    @Autowired lateinit var auditService: IAuditService
    @Autowired lateinit var validationCache: ApiKeyValidationCache

    private lateinit var owner: User

    @BeforeEach
    fun setUp() {
        owner = userRepository.save(User(email = "key-${System.nanoTime()}@example.com", name = "Key Owner"))
        validationCache.clear()
        asAdmin()
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
        validationCache.clear()
    }

    /** Most of these need a caller that may grant scopes; the escalation rule is tested too. */
    private fun asAdmin() {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            "admin", null, listOf(SimpleGrantedAuthority(Permission.ADMIN.authority))
        )
    }

    private fun asHolderOf(vararg permissions: Permission) {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            "limited", null, permissions.map { SimpleGrantedAuthority(it.authority) }
        )
    }

    private fun create(
        scopes: List<String>? = null,
        preset: ApiKeyPreset? = null,
        ttlDays: Int? = null,
        orgSlug: String? = null,
        neverExpires: Boolean = false
    ) = apiKeyService.createApiKey(
        CreateApiKeyRequest(
            ownerId = owner.id,
            label = "test key",
            ownerType = OwnerType.USER,
            ttlDays = ttlDays,
            scopes = scopes,
            preset = preset,
            orgSlug = orgSlug,
            neverExpires = neverExpires
        )
    )

    // --- Scopes -----------------------------------------------------------

    @Test
    fun `a key carries the scopes it was created with`() {
        val created = create(scopes = listOf("attestations:write", "trails:write"))

        assertEquals(listOf("attestations:write", "trails:write"), created.scopes)
        assertEquals(
            setOf(Permission.ATTESTATIONS_WRITE, Permission.TRAILS_WRITE),
            apiKeyRepository.findById(created.id)!!.scopes
        )
    }

    @Test
    fun `a key created with no scopes is read-only, not all-powerful`() {
        val created = create()

        assertEquals(Permission.DEFAULT_MINIMAL.map { it.scope }.sorted(), created.scopes)
        assertFalse(created.scopes.contains("admin"))
        assertTrue(created.scopes.all { it.endsWith(":read") })
    }

    @Test
    fun `an empty scope list is treated as none requested, not as everything`() {
        assertEquals(Permission.DEFAULT_MINIMAL.map { it.scope }.sorted(), create(scopes = emptyList()).scopes)
    }

    @Test
    fun `the CI preset grants what a pipeline needs and nothing administrative`() {
        val created = create(preset = ApiKeyPreset.CI_PIPELINE)

        assertTrue(created.scopes.containsAll(listOf("attestations:write", "assert:execute", "trails:write")))
        assertFalse(created.scopes.contains("admin"))
        assertFalse(created.scopes.contains("flows:write"))
    }

    @Test
    fun `an unknown scope is refused rather than silently dropped`() {
        val error = assertThrows<BadRequestException> { create(scopes = listOf("flows:read", "flows:destroy")) }

        assertTrue(error.message!!.contains("flows:destroy"))
    }

    // --- No privilege escalation -------------------------------------------

    @Test
    fun `a caller cannot grant a scope it does not hold`() {
        asHolderOf(Permission.ATTESTATIONS_WRITE)

        val error = assertThrows<ForbiddenException> { create(scopes = listOf("admin")) }

        assertTrue(error.message!!.contains("admin"))
    }

    @Test
    fun `a caller can grant scopes it does hold`() {
        asHolderOf(Permission.ATTESTATIONS_WRITE, Permission.TRAILS_WRITE)

        val created = create(scopes = listOf("attestations:write"))

        assertEquals(listOf("attestations:write"), created.scopes)
    }

    @Test
    fun `an admin caller may grant anything`() {
        asAdmin()

        assertEquals(listOf("admin"), create(scopes = listOf("admin")).scopes)
    }

    @Test
    fun `an unauthenticated caller can only create a read-only key`() {
        SecurityContextHolder.clearContext()

        assertThrows<ForbiddenException> { create(scopes = listOf("flows:write")) }
        // The minimal set is still allowed, so bootstrapping a read-only key stays possible.
        assertEquals(Permission.DEFAULT_MINIMAL.map { it.scope }.sorted(), create().scopes)
    }

    // --- TTL --------------------------------------------------------------

    @Test
    fun `omitting a TTL uses the default rather than creating a key that never expires`() {
        val created = create()

        assertNotNull(created.expiresAt)
        assertNotNull(created.ttlDays)
    }

    @Test
    fun `a TTL beyond the maximum is refused`() {
        val error = assertThrows<BadRequestException> { create(ttlDays = 3_650) }

        assertTrue(error.message!!.contains("maximum"))
    }

    @Test
    fun `a non-expiring key requires an explicit override`() {
        val error = assertThrows<BadRequestException> { create(neverExpires = true) }

        assertTrue(error.message!!.contains("allow-non-expiring"))
    }

    @Test
    fun `a zero or negative TTL is refused`() {
        assertThrows<BadRequestException> { create(ttlDays = 0) }
        assertThrows<BadRequestException> { create(ttlDays = -1) }
    }

    @Test
    fun `a key nearing expiry is flagged so a pipeline can be warned before it breaks`() {
        val created = create(ttlDays = 3)

        assertTrue(created.expiringSoon)
        assertNotNull(created.daysUntilExpiry)
        assertTrue(created.daysUntilExpiry!! <= 3)
    }

    @Test
    fun `a key with plenty of life left is not flagged`() {
        assertFalse(create(ttlDays = 60).expiringSoon)
    }

    // --- Validation -------------------------------------------------------

    @Test
    fun `a valid key validates and reports its scopes`() {
        val created = create(preset = ApiKeyPreset.CI_PIPELINE)

        val validated = apiKeyService.validateApiKey(created.plainTextKey)

        assertNotNull(validated)
        assertTrue(validated!!.scopes.contains("attestations:write"))
    }

    @Test
    fun `an unknown key is refused as unknown`() {
        val result = apiKeyService.validateWithReason("fsp_${"0".repeat(64)}")

        assertNull(result.response)
        assertEquals(AuthFailureReason.UNKNOWN, result.reason)
    }

    @Test
    fun `something that is not a key at all is refused as malformed`() {
        assertEquals(AuthFailureReason.MALFORMED, apiKeyService.validateWithReason("hello").reason)
        assertEquals(AuthFailureReason.MALFORMED, apiKeyService.validateWithReason("").reason)
    }

    @Test
    fun `a revoked key is refused, and says so`() {
        val created = create()
        assertNotNull(apiKeyService.validateApiKey(created.plainTextKey))

        apiKeyService.revokeApiKey(created.id)

        val result = apiKeyService.validateWithReason(created.plainTextKey)
        assertNull(result.response)
        assertEquals(AuthFailureReason.REVOKED, result.reason)
    }

    @Test
    fun `revocation takes effect on the next request, not after the cache TTL`() {
        val created = create()
        // Populate the cache.
        assertNotNull(apiKeyService.validateApiKey(created.plainTextKey))

        apiKeyService.revokeApiKey(created.id)

        assertNull(apiKeyService.validateApiKey(created.plainTextKey))
    }

    @Test
    fun `an expired key is refused, and says so`() {
        val created = create(ttlDays = 1)
        val key = apiKeyRepository.findById(created.id)!!
        // Rewrite the row with an expiry in the past; expiresAt is immutable on the entity.
        apiKeyRepository.save(
            com.factstore.core.domain.ApiKey(
                id = key.id,
                ownerId = key.ownerId,
                ownerType = key.ownerType,
                label = key.label,
                keyPrefix = key.keyPrefix,
                hashedKey = key.hashedKey,
                isActive = true,
                createdAt = key.createdAt,
                ttlDays = key.ttlDays,
                expiresAt = Instant.now().minusSeconds(60)
            ).also { it.scopes = key.scopes }
        )
        validationCache.clear()

        val result = apiKeyService.validateWithReason(created.plainTextKey)
        assertNull(result.response)
        assertEquals(AuthFailureReason.EXPIRED, result.reason)
    }

    @Test
    fun `a cache hit still honours a key that has since been revoked`() {
        val created = create()
        apiKeyService.validateApiKey(created.plainTextKey)
        val cachedBefore = validationCache.size

        val key = apiKeyRepository.findById(created.id)!!
        key.isActive = false
        apiKeyRepository.save(key)

        // The cache is not consulted for the decision, only to skip the BCrypt comparison.
        assertTrue(cachedBefore > 0)
        assertNull(apiKeyService.validateApiKey(created.plainTextKey))
    }

    // --- Rotation ---------------------------------------------------------

    @Test
    fun `rotation issues a new key with the same scopes`() {
        val original = create(preset = ApiKeyPreset.CI_PIPELINE, orgSlug = "acme")

        val rotated = apiKeyService.rotateApiKey(original.id, overlapHours = 24)

        assertNotEquals(original.id, rotated.id)
        assertNotEquals(original.plainTextKey, rotated.plainTextKey)
        assertEquals(original.scopes, rotated.scopes)
        assertEquals("acme", rotated.orgSlug)
        assertEquals(original.id, rotated.rotatedFromId)
    }

    @Test
    fun `both keys work during the overlap window, so a pipeline can roll over`() {
        val original = create()

        val rotated = apiKeyService.rotateApiKey(original.id, overlapHours = 24)

        assertNotNull(apiKeyService.validateApiKey(rotated.plainTextKey))
        assertNotNull(apiKeyService.validateApiKey(original.plainTextKey))
    }

    @Test
    fun `the old key stops working once the overlap window closes`() {
        val original = create()

        apiKeyService.rotateApiKey(original.id, overlapHours = 0)

        assertNull(apiKeyService.validateApiKey(original.plainTextKey))
    }

    @Test
    fun `a key cannot be rotated twice`() {
        val original = create()
        apiKeyService.rotateApiKey(original.id, overlapHours = 1)

        assertThrows<BadRequestException> { apiKeyService.rotateApiKey(original.id, overlapHours = 1) }
    }

    @Test
    fun `rotating an unknown key is a not-found`() {
        assertThrows<NotFoundException> { apiKeyService.rotateApiKey(UUID.randomUUID(), null) }
    }

    // --- Tenant binding ---------------------------------------------------

    @Test
    fun `a key can be bound to an organisation`() {
        val created = create(orgSlug = "acme")

        assertEquals("acme", created.orgSlug)
        assertEquals("acme", apiKeyService.validateApiKey(created.plainTextKey)!!.orgSlug)
    }

    // --- Auditing and redaction -------------------------------------------

    @Test
    fun `key lifecycle events are audited by prefix, never by key material`() {
        val created = create(scopes = listOf("attestations:write"))
        apiKeyService.rotateApiKey(created.id, overlapHours = 1)
        apiKeyService.revokeApiKey(created.id)

        listOf(
            AuditEventType.API_KEY_CREATED,
            AuditEventType.API_KEY_ROTATED,
            AuditEventType.API_KEY_REVOKED
        ).forEach { type ->
            val events = auditService.queryEvents(eventType = type, page = 0, size = 50).events
            assertTrue(events.isNotEmpty(), "expected a $type event")
        }
    }

    @Test
    fun `no audit entry contains the key material`() {
        val created = create(scopes = listOf("attestations:write"))
        apiKeyService.revokeApiKey(created.id)

        val secret = created.plainTextKey
        listOf(AuditEventType.API_KEY_CREATED, AuditEventType.API_KEY_REVOKED).forEach { type ->
            auditService.queryEvents(eventType = type, page = 0, size = 50).events.forEach { event ->
                assertFalse(
                    event.payload.contains(secret),
                    "an audit payload must never contain a working credential"
                )
                // The 64 hex characters after the prefix are the secret; the 12-char prefix is fine.
                assertFalse(event.payload.contains(secret.substringAfter('_')))
            }
        }
    }
}
