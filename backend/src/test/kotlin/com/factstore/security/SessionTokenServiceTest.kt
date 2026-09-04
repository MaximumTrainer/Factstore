package com.factstore.security

import com.factstore.application.auth.SessionTokenService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Session token signing and verification (#156 FR-2.4, FR-3.1).
 *
 * The old implementation was hand-rolled HMAC in two places and shipped with a working
 * default secret; both are the kind of thing that hands out authentication for free, so the
 * replacement is pinned here.
 */
class SessionTokenServiceTest {

    private val secret = "a-test-secret-that-is-definitely-long-enough-for-hs256"

    private fun service(
        secret: String = this.secret,
        issuer: String = "openfactstore",
        skew: Long = 60
    ) = SessionTokenService(secret, issuer, skew)

    private fun anHourFromNow() = Instant.now().plus(1, ChronoUnit.HOURS)

    @Test
    fun `a token round-trips its subject and session id`() {
        val service = service()

        val token = service.issue("user-1", "session-abc", "a@example.com", anHourFromNow())
        val verified = service.verify(token)

        assertNotNull(verified)
        assertEquals("user-1", verified!!.subject)
        assertEquals("session-abc", verified.sessionId)
    }

    @Test
    fun `a token signed with a different secret is rejected`() {
        val token = service().issue("user-1", "session-abc", null, anHourFromNow())

        val other = service(secret = "a-completely-different-secret-also-long-enough!!")

        assertNull(other.verify(token))
    }

    @Test
    fun `a tampered payload is rejected`() {
        val token = service().issue("user-1", "session-abc", null, anHourFromNow())
        val parts = token.split(".")
        // Swap the payload for one claiming a different subject; the signature no longer matches.
        val forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
            """{"sub":"user-2","sid":"session-abc","iss":"openfactstore","exp":${
                Instant.now().plusSeconds(3600).epochSecond
            }}""".toByteArray()
        )

        assertNull(service().verify("${parts[0]}.$forgedPayload.${parts[2]}"))
    }

    @Test
    fun `an expired token is rejected`() {
        val service = service(skew = 0)
        val token = service.issue("user-1", "session-abc", null, Instant.now().minusSeconds(120))

        assertNull(service.verify(token))
    }

    @Test
    fun `a token from another issuer is rejected`() {
        val token = service(issuer = "somebody-else").issue("u", "s", null, anHourFromNow())

        assertNull(service().verify(token))
    }

    @Test
    fun `garbage is rejected without throwing`() {
        val service = service()

        assertNull(service.verify("not-a-token"))
        assertNull(service.verify(""))
        assertNull(service.verify("a.b.c"))
    }

    @Test
    fun `an unsigned token is not accepted`() {
        // The "alg: none" attack: a well-formed JWT with an empty signature.
        val header = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"alg":"none","typ":"JWT"}""".toByteArray())
        val payload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
            """{"sub":"user-1","sid":"s","iss":"openfactstore","exp":${
                Instant.now().plusSeconds(3600).epochSecond
            }}""".toByteArray()
        )

        assertNull(service().verify("$header.$payload."))
    }

    // --- The secret has no safe default -----------------------------------

    @Test
    fun `an unset secret is not usable`() {
        val service = service(secret = "")

        assertFalse(service.isConfigured())
        assertTrue(service.secretProblem()!!.contains("SSO_JWT_SECRET"))
        assertThrows<IllegalStateException> { service.issue("u", "s", null, anHourFromNow()) }
    }

    @Test
    fun `the placeholder that used to ship as a working default is refused`() {
        val service = service(secret = "changeme-in-production")

        assertFalse(service.isConfigured())
        assertTrue(service.secretProblem()!!.contains("placeholder"))
    }

    @Test
    fun `every known placeholder is refused`() {
        SessionTokenService.INSECURE_DEFAULTS.forEach { placeholder ->
            assertFalse(service(secret = placeholder).isConfigured(), "should refuse '$placeholder'")
        }
    }

    @Test
    fun `a secret too short for HS256 is refused`() {
        val service = service(secret = "short")

        assertFalse(service.isConfigured())
        assertTrue(service.secretProblem()!!.contains("${SessionTokenService.MIN_SECRET_BYTES} bytes"))
    }

    @Test
    fun `a real secret of sufficient length is usable`() {
        assertTrue(service().isConfigured())
        assertNull(service().secretProblem())
    }

    @Test
    fun `the token carries no role or organisation, so neither can be forged into it`() {
        val token = service().issue("user-1", "session-abc", "a@example.com", anHourFromNow())
        val payload = String(
            java.util.Base64.getUrlDecoder().decode(
                token.split(".")[1].let { it + "=".repeat((4 - it.length % 4) % 4) }
            )
        )

        assertFalse(payload.contains("role"), "role is resolved per request, never carried: $payload")
        assertFalse(payload.contains("\"org\""), "organisation lives on the session row: $payload")
    }
}
