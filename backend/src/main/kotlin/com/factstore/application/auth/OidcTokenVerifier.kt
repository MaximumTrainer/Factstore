package com.factstore.application.auth

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.JWSKeySelector
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import com.factstore.exception.BadRequestException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Verifies an identity provider's ID token (#156 FR-2).
 *
 * This replaces `SsoConfigService.parseJwtClaims()`, which base64-decoded the payload and
 * trusted it. That made the authentication decision itself on unverified input: anyone able to
 * reach the callback could present a self-made token naming any email address and be signed in
 * as that person. TLS protects the transport; it says nothing about who minted the token.
 *
 * What is checked here:
 *  - the **signature**, against the provider's JWKS, with keys cached and rotation handled;
 *  - `iss` — matches the configured issuer exactly;
 *  - `aud` — contains our client id;
 *  - `exp` / `nbf` — with a small configurable clock skew;
 *  - `nonce` — matches the value bound to this login attempt, so a token captured from another
 *    flow cannot be replayed into ours.
 */
@Component
class OidcTokenVerifier(
    @Value("\${sso.jwt.clock-skew-seconds:60}") private val clockSkewSeconds: Long
) {

    private val log = LoggerFactory.getLogger(OidcTokenVerifier::class.java)

    /**
     * One processor per (issuer, jwksUri, clientId). Nimbus's JWK source caches keys and
     * refetches on an unknown `kid`, which is what handles provider key rotation.
     */
    private val processors = ConcurrentHashMap<String, DefaultJWTProcessor<SecurityContext>>()

    /**
     * @param expectedNonce the nonce generated for this login attempt; required, because a
     *   token without one cannot be tied to a request we started.
     */
    fun verify(
        idToken: String,
        issuer: String,
        jwksUri: String,
        clientId: String,
        expectedNonce: String
    ): Map<String, Any> {
        val processor = processors.computeIfAbsent("$issuer|$jwksUri|$clientId") {
            buildProcessor(issuer, jwksUri, clientId)
        }

        val claims: JWTClaimsSet = try {
            processor.process(idToken, null)
        } catch (ex: Exception) {
            // Deliberately terse: the caller is a login flow, and the detail belongs in the log,
            // not in a response that an attacker can probe.
            log.warn("ID token rejected for issuer=$issuer: ${ex.javaClass.simpleName}: ${ex.message}")
            throw BadRequestException("The identity provider's token could not be verified")
        }

        val nonce = claims.getStringClaim("nonce")
        if (nonce.isNullOrBlank() || !constantTimeEquals(nonce, expectedNonce)) {
            log.warn("ID token nonce mismatch for issuer=$issuer")
            throw BadRequestException("The identity provider's token could not be verified")
        }

        return claims.claims
    }

    private fun buildProcessor(
        issuer: String,
        jwksUri: String,
        clientId: String
    ): DefaultJWTProcessor<SecurityContext> {
        val jwkSource: JWKSource<SecurityContext> =
            JWKSourceBuilder.create<SecurityContext>(URL(jwksUri))
                .retrying(true)
                .build()

        val keySelector: JWSKeySelector<SecurityContext> = JWSVerificationKeySelector(
            setOf(JWSAlgorithm.RS256, JWSAlgorithm.RS384, JWSAlgorithm.RS512, JWSAlgorithm.ES256),
            jwkSource
        )

        val claimsVerifier = DefaultJWTClaimsVerifier<SecurityContext>(
            clientId,
            JWTClaimsSet.Builder().issuer(issuer).build(),
            setOf("sub", "iat", "exp", "aud", "iss")
        )
        claimsVerifier.maxClockSkew = clockSkewSeconds.toInt()

        return DefaultJWTProcessor<SecurityContext>().apply {
            jwsKeySelector = keySelector
            jwtClaimsSetVerifier = claimsVerifier
        }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }
}
