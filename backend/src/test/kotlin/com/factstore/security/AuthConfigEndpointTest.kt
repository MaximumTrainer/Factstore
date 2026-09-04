package com.factstore.security

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.context.TestSecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional

/**
 * Whether this instance enforces authentication, answered to an unauthenticated caller
 * (#156 FR-6.3).
 *
 * The UI has to gate exactly when the server gates, and `security.enforce-auth` deliberately
 * defaults to `false` during the #155 rollout. Without this, the router guard had to guess:
 * it treated "no principal" as "must sign in", so on a permissive instance every route
 * bounced to `/login` and the whole UI was unreachable.
 *
 * It must answer **without** a credential, because a client has to be able to ask before it
 * has one. That is safe: it reveals only what an unauthenticated request already reveals by
 * being accepted or refused.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthConfigEndpointTest {

    @Autowired lateinit var mockMvc: MockMvc

    @BeforeEach
    fun clearSecurity() {
        SecurityContextHolder.clearContext()
        TestSecurityContextHolder.clearContext()
    }

    @Test
    fun `an unauthenticated caller is told whether authentication is enforced`() {
        mockMvc.get("/api/v1/auth/config").andExpect {
            status { isOk() }
            jsonPath("$.enforceAuth") { exists() }
        }
    }

    @Test
    fun `the answer matches this instance's configuration`() {
        // The test profile leaves enforcement off, as the shipped default does.
        mockMvc.get("/api/v1/auth/config").andExpect {
            status { isOk() }
            jsonPath("$.enforceAuth") { value(false) }
        }
    }

    @Test
    fun `it discloses nothing beyond enforcement`() {
        // A public endpoint should not become a place to learn about the deployment: no
        // issuer, no client id, no bootstrap state, no credential material.
        mockMvc.get("/api/v1/auth/config").andExpect {
            status { isOk() }
            jsonPath("$.*") { value(org.hamcrest.Matchers.hasSize<Any>(1)) }
        }
    }
}
