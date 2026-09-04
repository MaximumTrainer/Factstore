package com.factstore.security

import com.factstore.application.auth.SessionService
import com.factstore.core.domain.AuthProvider
import com.factstore.core.domain.MemberRole
import com.factstore.core.domain.OrganisationMembership
import com.factstore.core.domain.SessionRevocationReason
import com.factstore.core.domain.User
import com.factstore.core.port.outbound.IOrganisationMembershipRepository
import com.factstore.core.port.outbound.IUserRepository
import com.factstore.core.port.outbound.IUserSessionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * The session lifecycle (#156 FR-3, FR-5).
 *
 * The behaviours pinned here are the ones the old implementation could not do at all:
 * revoke a session, list sessions, and have a role change take effect without a re-login.
 */
@SpringBootTest
@Transactional
class SessionServiceTest {

    @Autowired lateinit var sessionService: SessionService
    @Autowired lateinit var userRepository: IUserRepository
    @Autowired lateinit var membershipRepository: IOrganisationMembershipRepository
    @Autowired lateinit var sessionRepository: IUserSessionRepository

    private fun newUser(): User = userRepository.save(
        User(email = "user-${System.nanoTime()}@example.com", name = "Test User")
    )

    private fun member(userId: UUID, orgSlug: String, role: MemberRole) {
        membershipRepository.save(OrganisationMembership(orgSlug = orgSlug, userId = userId, role = role))
    }

    @Test
    fun `an issued session resolves to its user`() {
        val user = newUser()

        val issued = sessionService.issue(user.id, null, AuthProvider.OIDC)
        val principal = sessionService.resolve(issued.token)

        assertNotNull(principal)
        assertEquals(user.id, principal!!.userId)
        assertEquals(user.email, principal.email)
        assertEquals(issued.session.jti, principal.sessionId)
    }

    @Test
    fun `the role comes from the membership, not from the token`() {
        val user = newUser()
        val org = "org-${System.nanoTime()}"
        member(user.id, org, MemberRole.ADMIN)

        val issued = sessionService.issue(user.id, org, AuthProvider.OIDC)

        assertEquals(MemberRole.ADMIN, sessionService.resolve(issued.token)!!.role)
        assertTrue(sessionService.resolve(issued.token)!!.authorities.contains("SCOPE_admin"))
    }

    @Test
    fun `a role change takes effect on the next request, without re-signing in`() {
        val user = newUser()
        val org = "org-${System.nanoTime()}"
        member(user.id, org, MemberRole.ADMIN)
        val issued = sessionService.issue(user.id, org, AuthProvider.OIDC)
        assertEquals(MemberRole.ADMIN, sessionService.resolve(issued.token)!!.role)

        val membership = membershipRepository.findByOrgSlugAndUserId(org, user.id)!!
        membership.role = MemberRole.VIEWER
        membershipRepository.save(membership)

        // Same token, demoted principal.
        val after = sessionService.resolve(issued.token)!!
        assertEquals(MemberRole.VIEWER, after.role)
        assertFalse(after.authorities.contains("SCOPE_admin"))
    }

    @Test
    fun `a user with no membership in the session organisation is a viewer, not privileged`() {
        val user = newUser()

        val issued = sessionService.issue(user.id, "org-they-do-not-belong-to", AuthProvider.OIDC)

        assertEquals(MemberRole.VIEWER, sessionService.resolve(issued.token)!!.role)
    }

    // --- Revocation --------------------------------------------------------

    @Test
    fun `a revoked session stops working immediately, not at token expiry`() {
        val user = newUser()
        val issued = sessionService.issue(user.id, null, AuthProvider.OIDC)
        assertNotNull(sessionService.resolve(issued.token))

        sessionService.revoke(issued.session.jti, SessionRevocationReason.LOGOUT)

        // The token itself is still cryptographically valid and unexpired; the session is not.
        assertNull(sessionService.resolve(issued.token))
    }

    @Test
    fun `revoking an unknown session is a no-op rather than an error`() {
        assertFalse(sessionService.revoke("no-such-session", SessionRevocationReason.LOGOUT))
    }

    @Test
    fun `revoking twice reports the second attempt as a no-op`() {
        val issued = sessionService.issue(newUser().id, null, AuthProvider.OIDC)

        assertTrue(sessionService.revoke(issued.session.jti, SessionRevocationReason.LOGOUT))
        assertFalse(sessionService.revoke(issued.session.jti, SessionRevocationReason.LOGOUT))
    }

    @Test
    fun `an administrator can revoke every session a user holds`() {
        val user = newUser()
        val first = sessionService.issue(user.id, null, AuthProvider.OIDC)
        val second = sessionService.issue(user.id, null, AuthProvider.OIDC)

        val revoked = sessionService.revokeAllForUser(user.id, SessionRevocationReason.ADMIN_REVOKED)

        assertEquals(2, revoked)
        assertNull(sessionService.resolve(first.token))
        assertNull(sessionService.resolve(second.token))
    }

    @Test
    fun `sessions can be listed, and a revoked one drops out`() {
        val user = newUser()
        val first = sessionService.issue(user.id, null, AuthProvider.OIDC)
        sessionService.issue(user.id, null, AuthProvider.OIDC)
        assertEquals(2, sessionService.activeSessions(user.id).size)

        sessionService.revoke(first.session.jti, SessionRevocationReason.LOGOUT)

        assertEquals(1, sessionService.activeSessions(user.id).size)
    }

    // --- Expiry and refresh ------------------------------------------------

    @Test
    fun `an expired session does not resolve even with a valid-looking token`() {
        val user = newUser()
        val issued = sessionService.issue(user.id, null, AuthProvider.OIDC)

        val session = sessionRepository.findByJti(issued.session.jti)!!
        session.expiresAt = Instant.now().minusSeconds(1)
        sessionRepository.save(session)

        assertNull(sessionService.resolve(issued.token))
    }

    @Test
    fun `a session idle beyond the timeout is dead and stays dead`() {
        val user = newUser()
        val issued = sessionService.issue(user.id, null, AuthProvider.OIDC)

        val session = sessionRepository.findByJti(issued.session.jti)!!
        session.lastSeenAt = Instant.now().minus(2, ChronoUnit.DAYS)
        sessionRepository.save(session)

        assertNull(sessionService.resolve(issued.token))
        // Idling revokes it, so it cannot come back by being touched again.
        assertEquals(
            SessionRevocationReason.IDLE_TIMEOUT,
            sessionRepository.findByJti(issued.session.jti)!!.revokedReason
        )
    }

    @Test
    fun `refresh extends the session and issues a working token`() {
        val user = newUser()
        val issued = sessionService.issue(user.id, null, AuthProvider.OIDC)
        val originalExpiry = issued.expiresAt

        val session = sessionRepository.findByJti(issued.session.jti)!!
        session.expiresAt = Instant.now().plusSeconds(30)
        sessionRepository.save(session)

        val refreshed = sessionService.refresh(issued.token)

        assertNotNull(refreshed)
        assertTrue(refreshed!!.expiresAt.isAfter(Instant.now().plusSeconds(60)))
        assertNotNull(sessionService.resolve(refreshed.token))
        assertTrue(originalExpiry.isBefore(refreshed.expiresAt.plusSeconds(1)))
    }

    @Test
    fun `refresh cannot push a session past its absolute lifetime`() {
        val user = newUser()
        val issued = sessionService.issue(user.id, null, AuthProvider.OIDC)

        val session = sessionRepository.findByJti(issued.session.jti)!!
        val ceiling = Instant.now().plusSeconds(120)
        sessionRepository.save(
            com.factstore.core.domain.UserSession(
                id = session.id,
                jti = session.jti,
                userId = session.userId,
                orgSlug = session.orgSlug,
                provider = session.provider,
                createdAt = session.createdAt,
                lastSeenAt = Instant.now(),
                expiresAt = session.expiresAt,
                absoluteExpiresAt = ceiling
            )
        )

        val refreshed = sessionService.refresh(issued.token)!!

        assertFalse(refreshed.expiresAt.isAfter(ceiling), "refresh must respect the ceiling")
    }

    @Test
    fun `refresh of a revoked session fails`() {
        val issued = sessionService.issue(newUser().id, null, AuthProvider.OIDC)
        sessionService.revoke(issued.session.jti, SessionRevocationReason.LOGOUT)

        assertNull(sessionService.refresh(issued.token))
    }

    // --- Organisation switching -------------------------------------------

    @Test
    fun `switching organisation changes the role the session acts with`() {
        val user = newUser()
        val orgA = "org-a-${System.nanoTime()}"
        val orgB = "org-b-${System.nanoTime()}"
        member(user.id, orgA, MemberRole.ADMIN)
        member(user.id, orgB, MemberRole.VIEWER)
        val issued = sessionService.issue(user.id, orgA, AuthProvider.OIDC)
        assertEquals(MemberRole.ADMIN, sessionService.resolve(issued.token)!!.role)

        val switched = sessionService.switchOrganisation(issued.session.jti, orgB)

        assertNotNull(switched)
        assertEquals(orgB, switched!!.orgSlug)
        assertEquals(MemberRole.VIEWER, switched.role)
        assertEquals(MemberRole.VIEWER, sessionService.resolve(issued.token)!!.role)
    }

    @Test
    fun `a session cannot switch into an organisation the user does not belong to`() {
        val user = newUser()
        val org = "org-${System.nanoTime()}"
        member(user.id, org, MemberRole.ADMIN)
        val issued = sessionService.issue(user.id, org, AuthProvider.OIDC)

        assertNull(sessionService.switchOrganisation(issued.session.jti, "someone-elses-org"))
    }

    @Test
    fun `a session for a deleted user stops resolving`() {
        val user = newUser()
        val issued = sessionService.issue(user.id, null, AuthProvider.OIDC)
        assertNotNull(sessionService.resolve(issued.token))

        userRepository.deleteById(user.id)

        assertNull(sessionService.resolve(issued.token))
    }

    @Test
    fun `each session gets its own identifier`() {
        val user = newUser()

        val first = sessionService.issue(user.id, null, AuthProvider.OIDC)
        val second = sessionService.issue(user.id, null, AuthProvider.OIDC)

        assertFalse(first.session.jti == second.session.jti)
    }
}
