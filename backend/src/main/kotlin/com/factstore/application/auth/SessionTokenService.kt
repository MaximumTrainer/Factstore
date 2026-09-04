package com.factstore.application.auth

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.MACVerifier
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Date

/**
 * Issues and validates session tokens (#156 FR-2.4, FR-3).
 *
 * Uses Nimbus — already on the classpath via the OAuth2 client starter — rather than the
 * hand-rolled HMAC this replaces. Hand-rolled JWT handling existed in two places, and getting
 * signature verification subtly wrong is the single easiest way to hand out authentication for
 * free.
 *
 * The token deliberately carries almost nothing: a subject and a [SESSION_ID_CLAIM]. Role,
 * organisation and validity are read from the session row on every request, so a role change
 * or a revocation takes effect immediately rather than at the next sign-in (FR-3.3, FR-5.4).
 */
@Component
class SessionTokenService(
    @Value("\${sso.jwt.secret:}") private val configuredSecret: String,
    @Value("\${sso.jwt.issuer:openfactstore}") private val issuer: String,
    @Value("\${sso.jwt.clock-skew-seconds:60}") private val clockSkewSeconds: Long
) {

    private val log = LoggerFactory.getLogger(SessionTokenService::class.java)

    private val secretBytes: ByteArray by lazy {
        validateSecret(configuredSecret)
        configuredSecret.toByteArray(StandardCharsets.UTF_8)
    }

    /**
     * Signs a session token for [sessionId].
     *
     * @param subject the user id, as the token's `sub`
     */
    fun issue(subject: String, sessionId: String, email: String?, expiresAt: Instant): String {
        val now = Instant.now()
        val claims = JWTClaimsSet.Builder()
            .subject(subject)
            .issuer(issuer)
            .jwtID(sessionId)
            .claim(SESSION_ID_CLAIM, sessionId)
            .apply { if (email != null) claim("email", email) }
            .issueTime(Date.from(now))
            .notBeforeTime(Date.from(now))
            .expirationTime(Date.from(expiresAt))
            .build()

        val jwt = SignedJWT(JWSHeader(JWSAlgorithm.HS256), claims)
        jwt.sign(MACSigner(secretBytes))
        return jwt.serialize()
    }

    /**
     * Verifies the signature and time claims, returning the session id the token names.
     * Returns null for any token that is not verifiably ours and currently valid — the caller
     * cannot distinguish the reasons, and deliberately should not.
     */
    fun verify(token: String): VerifiedToken? {
        val jwt = try {
            SignedJWT.parse(token)
        } catch (_: Exception) {
            return null
        }

        return try {
            if (!jwt.verify(MACVerifier(secretBytes))) {
                log.debug("Session token signature did not verify")
                return null
            }
            val claims = jwt.jwtClaimsSet
            val now = Instant.now()

            if (claims.issuer != issuer) {
                log.debug("Session token issuer mismatch")
                return null
            }
            val expiry = claims.expirationTime?.toInstant() ?: return null
            if (now.isAfter(expiry.plusSeconds(clockSkewSeconds))) return null
            claims.notBeforeTime?.toInstant()?.let { notBefore ->
                if (now.isBefore(notBefore.minusSeconds(clockSkewSeconds))) return null
            }

            val sessionId = claims.getStringClaim(SESSION_ID_CLAIM) ?: claims.jwtid ?: return null
            val subject = claims.subject ?: return null
            VerifiedToken(subject = subject, sessionId = sessionId, expiresAt = expiry)
        } catch (ex: Exception) {
            log.debug("Session token rejected: ${ex.javaClass.simpleName}")
            null
        }
    }

    /** True when a usable secret is configured, so callers can fail fast at startup. */
    fun isConfigured(): Boolean = runCatching { validateSecret(configuredSecret) }.isSuccess

    fun secretProblem(): String? = runCatching { validateSecret(configuredSecret) }
        .exceptionOrNull()?.message

    private fun validateSecret(secret: String) {
        if (secret.isBlank()) {
            throw IllegalStateException(
                "sso.jwt.secret (SSO_JWT_SECRET) is not set. A session signing secret has no safe " +
                    "default: anyone who knows it can forge a session for any user. Generate one with " +
                    "`openssl rand -base64 48`."
            )
        }
        if (secret in INSECURE_DEFAULTS) {
            throw IllegalStateException(
                "sso.jwt.secret (SSO_JWT_SECRET) is set to the well-known placeholder '$secret'. " +
                    "Generate a real secret with `openssl rand -base64 48`."
            )
        }
        if (secret.toByteArray(StandardCharsets.UTF_8).size < MIN_SECRET_BYTES) {
            throw IllegalStateException(
                "sso.jwt.secret (SSO_JWT_SECRET) must be at least $MIN_SECRET_BYTES bytes for HS256; " +
                    "it is ${secret.toByteArray(StandardCharsets.UTF_8).size}."
            )
        }
    }

    companion object {
        const val SESSION_ID_CLAIM = "sid"

        /** HS256 keys shorter than the hash output weaken the MAC; Nimbus refuses them anyway. */
        const val MIN_SECRET_BYTES = 32

        /**
         * Values that must never be accepted. The first is the default this replaces: it shipped
         * as a working configuration behind nothing but a startup warning.
         */
        val INSECURE_DEFAULTS = setOf("changeme-in-production", "changeme", "secret", "change-me")
    }
}

data class VerifiedToken(val subject: String, val sessionId: String, val expiresAt: Instant)
