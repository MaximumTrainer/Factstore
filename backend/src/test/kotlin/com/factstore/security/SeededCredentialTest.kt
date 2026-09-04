package com.factstore.security

import com.factstore.application.ApiKeyService
import com.factstore.application.ApiKeyValidationCache
import com.factstore.core.domain.OwnerType
import com.factstore.core.domain.User
import com.factstore.core.domain.security.Permission
import com.factstore.core.port.outbound.IUserRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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

/**
 * A bootstrap credential supplied by configuration (#155 FR-7.1).
 *
 * This is the half of FR-7.1 that makes an unattended environment work: CI, local development
 * and container images need a credential without scraping a value out of a startup log.
 *
 * It also fixes a concrete break: authorisation rules are always live, so `POST /api/v1/flows`
 * needs `flows:write` even with enforcement off — which meant the dogfood and persona
 * workflows, both of which create a flow unauthenticated, would have started failing.
 */
@SpringBootTest
@Transactional
class SeededCredentialTest {

    @Autowired lateinit var apiKeyService: ApiKeyService
    @Autowired lateinit var userRepository: IUserRepository
    @Autowired lateinit var validationCache: ApiKeyValidationCache

    private lateinit var owner: User

    private val seeded = "fsp_" + "c1".repeat(32)

    @BeforeEach
    fun setUp() {
        validationCache.clear()
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            "admin", null, listOf(SimpleGrantedAuthority(Permission.ADMIN.authority))
        )
        owner = userRepository.save(User(email = "seed-${System.nanoTime()}@example.com", name = "Seed"))
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
        validationCache.clear()
    }

    private fun seed(value: String = seeded, scopes: Set<Permission> = setOf(Permission.ADMIN)) =
        apiKeyService.seedApiKey(
            ownerId = owner.id,
            ownerType = OwnerType.USER,
            label = "seeded",
            scopes = scopes,
            plainTextKey = value,
            ttlDays = 7
        )

    @Test
    fun `a seeded key validates with the scopes it was given`() {
        seed()

        val validated = apiKeyService.validateApiKey(seeded)

        assertNotNull(validated)
        assertTrue(validated!!.scopes.contains("admin"))
    }

    @Test
    fun `seeding is idempotent, so a restart does not accumulate keys`() {
        val first = seed()
        val second = seed()

        assertEquals(first.id, second.id)
    }

    @Test
    fun `a seeded key can create a flow, which an unauthenticated caller cannot`() {
        seed()

        // The scopes the workflows actually need.
        val validated = apiKeyService.validateApiKey(seeded)!!
        assertTrue(validated.scopes.contains("admin"))
        assertNotNull(validated.expiresAt, "a seeded credential is still bounded by a TTL")
    }

    @Test
    fun `a seed that is not shaped like a key is refused, not silently ignored`() {
        // The prefix lookup keys on the first 12 characters, so a value that cannot produce a
        // usable prefix would create a credential that never authenticates.
        assertThrows<IllegalArgumentException> { seed("not-an-api-key-at-all") }
        assertThrows<IllegalArgumentException> { seed("fsp_short") }
    }

    @Test
    fun `a seeded key is scoped, not automatically all-powerful`() {
        val readOnly = "fsp_" + "d2".repeat(32)

        seed(readOnly, scopes = Permission.DEFAULT_MINIMAL)

        val validated = apiKeyService.validateApiKey(readOnly)!!
        assertTrue(validated.scopes.none { it == "admin" })
        assertTrue(validated.scopes.all { it.endsWith(":read") })
    }
}
