package com.factstore.security

import com.factstore.adapter.inbound.web.SessionAuthFilter
import com.factstore.application.auth.SessionService
import com.factstore.core.domain.AuthProvider
import com.factstore.core.domain.MemberRole
import com.factstore.core.domain.OrganisationMembership
import com.factstore.core.domain.User
import com.factstore.core.port.outbound.IOrganisationMembershipRepository
import com.factstore.core.port.outbound.IUserRepository
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * The `/auth` surface (#156 FR-3, FR-4, FR-6.2), driven the way a client actually drives it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthEndpointTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var sessionService: SessionService
    @Autowired lateinit var userRepository: IUserRepository
    @Autowired lateinit var membershipRepository: IOrganisationMembershipRepository

    private fun newUser(): User = userRepository.save(
        User(email = "auth-${System.nanoTime()}@example.com", name = "Auth Test")
    )

    private fun member(userId: UUID, orgSlug: String, role: MemberRole) {
        membershipRepository.save(OrganisationMembership(orgSlug = orgSlug, userId = userId, role = role))
    }

    // --- /auth/me ---------------------------------------------------------

    @Test
    fun `me returns 401 with a Bearer challenge when unauthenticated`() {
        mockMvc.get("/api/v1/auth/me").andExpect {
            status { isUnauthorized() }
            header { string("WWW-Authenticate", "Bearer realm=\"factstore\"") }
        }
    }

    @Test
    fun `me returns the identity, organisation, role and permissions for a session`() {
        val user = newUser()
        val org = "org-${System.nanoTime()}"
        member(user.id, org, MemberRole.ADMIN)
        val issued = sessionService.issue(user.id, org, AuthProvider.OIDC)

        mockMvc.get("/api/v1/auth/me") {
            header("Authorization", "Bearer ${issued.token}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.type") { value("USER") }
            jsonPath("$.userId") { value(user.id.toString()) }
            jsonPath("$.email") { value(user.email) }
            jsonPath("$.orgSlug") { value(org) }
            jsonPath("$.role") { value("ADMIN") }
            jsonPath("$.permissions") { exists() }
            jsonPath("$.sessionId") { value(issued.session.jti) }
        }
    }

    @Test
    fun `me lists every organisation the user belongs to, for the switcher`() {
        val user = newUser()
        val orgA = "aaa-${System.nanoTime()}"
        val orgB = "bbb-${System.nanoTime()}"
        member(user.id, orgA, MemberRole.ADMIN)
        member(user.id, orgB, MemberRole.VIEWER)
        val issued = sessionService.issue(user.id, orgA, AuthProvider.OIDC)

        mockMvc.get("/api/v1/auth/me") {
            header("Authorization", "Bearer ${issued.token}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.organisations.length()") { value(2) }
            jsonPath("$.organisations[0].orgSlug") { value(orgA) }
            jsonPath("$.organisations[1].role") { value("VIEWER") }
        }
    }

    @Test
    fun `a viewer's permissions do not include admin`() {
        val user = newUser()
        val org = "org-${System.nanoTime()}"
        member(user.id, org, MemberRole.VIEWER)
        val issued = sessionService.issue(user.id, org, AuthProvider.OIDC)

        val body = mockMvc.get("/api/v1/auth/me") {
            header("Authorization", "Bearer ${issued.token}")
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString

        assert(!body.contains("\"admin\"")) { "a viewer must not hold the admin permission: $body" }
        assert(body.contains("flows:read")) { "a viewer should be able to read flows: $body" }
    }

    @Test
    fun `the session cookie authenticates a read`() {
        val user = newUser()
        val issued = sessionService.issue(user.id, null, AuthProvider.OIDC)

        mockMvc.get("/api/v1/auth/me") {
            cookie(Cookie(SessionAuthFilter.SESSION_COOKIE, issued.token))
        }.andExpect {
            status { isOk() }
            jsonPath("$.userId") { value(user.id.toString()) }
        }
    }

    @Test
    fun `a garbage token is unauthenticated rather than an error`() {
        mockMvc.get("/api/v1/auth/me") {
            header("Authorization", "Bearer not-a-real-token")
        }.andExpect { status { isUnauthorized() } }
    }

    // --- Logout -----------------------------------------------------------

    @Test
    fun `logout revokes the session so the next request is unauthenticated`() {
        val user = newUser()
        val issued = sessionService.issue(user.id, null, AuthProvider.OIDC)

        mockMvc.post("/api/v1/auth/logout") {
            header("Authorization", "Bearer ${issued.token}")
            header(SessionAuthFilter.CLIENT_HEADER, "web")
        }.andExpect {
            status { isNoContent() }
            header { string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")) }
        }

        mockMvc.get("/api/v1/auth/me") {
            header("Authorization", "Bearer ${issued.token}")
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `logout works without a valid session, so a stale cookie can always be cleared`() {
        mockMvc.post("/api/v1/auth/logout") {
            header(SessionAuthFilter.CLIENT_HEADER, "web")
        }.andExpect {
            status { isNoContent() }
            header { string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")) }
        }
    }

    // --- Refresh ----------------------------------------------------------

    @Test
    fun `refresh returns a new expiry and sets a fresh cookie`() {
        val user = newUser()
        val issued = sessionService.issue(user.id, null, AuthProvider.OIDC)

        mockMvc.post("/api/v1/auth/refresh") {
            header("Authorization", "Bearer ${issued.token}")
            header(SessionAuthFilter.CLIENT_HEADER, "web")
        }.andExpect {
            status { isOk() }
            jsonPath("$.expiresAt") { exists() }
            header { string("Set-Cookie", org.hamcrest.Matchers.containsString("HttpOnly")) }
        }
    }

    @Test
    fun `refresh without a credential is 401`() {
        mockMvc.post("/api/v1/auth/refresh") {
            header(SessionAuthFilter.CLIENT_HEADER, "web")
        }.andExpect { status { isUnauthorized() } }
    }

    // --- Sessions ---------------------------------------------------------

    @Test
    fun `a user can list their own sessions and see which one is current`() {
        val user = newUser()
        val first = sessionService.issue(user.id, null, AuthProvider.OIDC)
        sessionService.issue(user.id, null, AuthProvider.OIDC)

        mockMvc.get("/api/v1/auth/sessions") {
            header("Authorization", "Bearer ${first.token}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[?(@.sessionId == '${first.session.jti}')].current") { value(true) }
        }
    }

    @Test
    fun `a user can revoke another of their own sessions`() {
        val user = newUser()
        val current = sessionService.issue(user.id, null, AuthProvider.OIDC)
        val other = sessionService.issue(user.id, null, AuthProvider.OIDC)

        mockMvc.delete("/api/v1/auth/sessions/${other.session.jti}") {
            header("Authorization", "Bearer ${current.token}")
            header(SessionAuthFilter.CLIENT_HEADER, "web")
        }.andExpect { status { isNoContent() } }

        mockMvc.get("/api/v1/auth/me") {
            header("Authorization", "Bearer ${other.token}")
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `a user cannot revoke someone else's session`() {
        val mine = sessionService.issue(newUser().id, null, AuthProvider.OIDC)
        val theirs = sessionService.issue(newUser().id, null, AuthProvider.OIDC)

        mockMvc.delete("/api/v1/auth/sessions/${theirs.session.jti}") {
            header("Authorization", "Bearer ${mine.token}")
            header(SessionAuthFilter.CLIENT_HEADER, "web")
        }.andExpect { status { isNotFound() } }

        // Still working, because the attempt failed.
        mockMvc.get("/api/v1/auth/me") {
            header("Authorization", "Bearer ${theirs.token}")
        }.andExpect { status { isOk() } }
    }

    // --- Organisation switching -------------------------------------------

    @Test
    fun `switching organisation changes the role reported by me`() {
        val user = newUser()
        val orgA = "org-a-${System.nanoTime()}"
        val orgB = "org-b-${System.nanoTime()}"
        member(user.id, orgA, MemberRole.ADMIN)
        member(user.id, orgB, MemberRole.VIEWER)
        val issued = sessionService.issue(user.id, orgA, AuthProvider.OIDC)

        mockMvc.post("/api/v1/auth/organisation") {
            header("Authorization", "Bearer ${issued.token}")
            header(SessionAuthFilter.CLIENT_HEADER, "web")
            contentType = MediaType.APPLICATION_JSON
            content = """{"orgSlug":"$orgB"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.orgSlug") { value(orgB) }
            jsonPath("$.role") { value("VIEWER") }
        }

        mockMvc.get("/api/v1/auth/me") {
            header("Authorization", "Bearer ${issued.token}")
        }.andExpect { jsonPath("$.orgSlug") { value(orgB) } }
    }

    @Test
    fun `switching into an organisation the user does not belong to is refused`() {
        val user = newUser()
        val org = "org-${System.nanoTime()}"
        member(user.id, org, MemberRole.ADMIN)
        val issued = sessionService.issue(user.id, org, AuthProvider.OIDC)

        mockMvc.post("/api/v1/auth/organisation") {
            header("Authorization", "Bearer ${issued.token}")
            header(SessionAuthFilter.CLIENT_HEADER, "web")
            contentType = MediaType.APPLICATION_JSON
            content = """{"orgSlug":"not-my-org"}"""
        }.andExpect { status { isNotFound() } }
    }

    // --- CSRF -------------------------------------------------------------

    @Test
    fun `a cookie is not honoured on a mutating request without the client header`() {
        val user = newUser()
        val issued = sessionService.issue(user.id, null, AuthProvider.OIDC)

        // What a cross-site form post looks like: the cookie rides along, but the header
        // cannot be set from another origin.
        mockMvc.post("/api/v1/auth/organisation") {
            cookie(Cookie(SessionAuthFilter.SESSION_COOKIE, issued.token))
            contentType = MediaType.APPLICATION_JSON
            content = """{"orgSlug":"anything"}"""
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `a cookie plus the client header is honoured on a mutating request`() {
        val user = newUser()
        val org = "org-${System.nanoTime()}"
        member(user.id, org, MemberRole.MEMBER)
        val issued = sessionService.issue(user.id, org, AuthProvider.OIDC)

        mockMvc.post("/api/v1/auth/organisation") {
            cookie(Cookie(SessionAuthFilter.SESSION_COOKIE, issued.token))
            header(SessionAuthFilter.CLIENT_HEADER, "web")
            contentType = MediaType.APPLICATION_JSON
            content = """{"orgSlug":"$org"}"""
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `a bearer token needs no client header, since it is not an ambient credential`() {
        val user = newUser()
        val org = "org-${System.nanoTime()}"
        member(user.id, org, MemberRole.MEMBER)
        val issued = sessionService.issue(user.id, org, AuthProvider.OIDC)

        mockMvc.post("/api/v1/auth/organisation") {
            header("Authorization", "Bearer ${issued.token}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"orgSlug":"$org"}"""
        }.andExpect { status { isOk() } }
    }
}
