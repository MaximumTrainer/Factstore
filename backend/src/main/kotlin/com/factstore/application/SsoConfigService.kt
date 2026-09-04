package com.factstore.application

import com.factstore.adapter.outbound.UrlValidator
import com.factstore.application.auth.OidcTokenVerifier
import com.factstore.application.auth.SessionService
import com.factstore.core.domain.AuthProvider
import com.factstore.core.domain.security.RoleModel
import com.factstore.core.domain.MemberRole
import com.factstore.core.domain.OrganisationMembership
import com.factstore.core.domain.SsoConfig
import com.factstore.core.domain.User
import com.factstore.core.port.inbound.ISsoConfigService
import com.factstore.core.port.outbound.IOrganisationMembershipRepository
import com.factstore.core.port.outbound.ISsoConfigRepository
import com.factstore.core.port.outbound.IUserRepository
import com.factstore.dto.CreateSsoConfigRequest
import com.factstore.dto.SsoCallbackResponse
import com.factstore.dto.SsoConfigResponse
import com.factstore.dto.SsoLoginUrlResponse
import com.factstore.dto.SsoTestConnectionResponse
import com.factstore.dto.UpdateSsoConfigRequest
import com.factstore.exception.BadRequestException
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestTemplate
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
@Transactional
class SsoConfigService(
    private val ssoConfigRepository: ISsoConfigRepository,
    private val userRepository: IUserRepository,
    private val membershipRepository: IOrganisationMembershipRepository,
    @Qualifier("ssoRestTemplate") private val restTemplate: RestTemplate,
    private val objectMapper: ObjectMapper,
    private val oidcTokenVerifier: OidcTokenVerifier,
    private val sessionService: SessionService
) : ISsoConfigService {

    companion object {
        /**
         * Role privilege ordering for [resolveRole]: the first role in this list that matches
         * a user's IdP groups is selected, ensuring the highest-privilege role always wins.
         * Update this list if the [MemberRole] hierarchy changes.
         */
        internal val ROLE_PRIVILEGE_ORDER = listOf(
            MemberRole.ADMIN, MemberRole.MEMBER, MemberRole.VIEWER, MemberRole.SERVICE_ACCOUNT
        )
    }

    private val log = LoggerFactory.getLogger(SsoConfigService::class.java)

    /**
     * Short-lived OIDC state tokens keyed by state value, expiring after 10 minutes.
     *
     * NOTE: This in-memory map is **not** shared across instances in a multi-replica deployment.
     * If the OIDC callback lands on a different instance than the one that initiated the login,
     * the state will be missing and the login will fail.  For multi-instance deployments,
     * replace this map with a shared store (e.g., Redis or a DB-backed cache with TTL).
     */
    internal val pendingStates = ConcurrentHashMap<String, PendingOidcState>()

    data class PendingOidcState(
        val orgSlug: String,
        /** The redirect URI sent to the IdP; reused verbatim in the token exchange. */
        val redirectUri: String,
        val expiresAt: Instant,
        /**
         * Bound to this login attempt and required to appear in the ID token, so a token
         * obtained from a different flow cannot be replayed into ours (#156 FR-2.2).
         */
        val nonce: String = "",
        /**
         * PKCE verifier (#156 FR-1.3). Proves the party redeeming the authorization code is
         * the one that started the flow, so an intercepted code is useless on its own.
         */
        val codeVerifier: String = ""
    )

    private val secureRandom = java.security.SecureRandom()

    // -------------------------------------------------------------------------
    // CRUD
    // -------------------------------------------------------------------------

    override fun createSsoConfig(orgSlug: String, request: CreateSsoConfigRequest): SsoConfigResponse {
        if (ssoConfigRepository.existsByOrgSlug(orgSlug)) {
            throw ConflictException("SSO configuration already exists for organisation '$orgSlug'")
        }
        val normalizedUrl = request.issuerUrl.trimEnd('/')
        validateIssuerUrl(normalizedUrl)
        val config = SsoConfig(
            orgSlug = orgSlug,
            provider = request.provider,
            issuerUrl = normalizedUrl,
            clientId = request.clientId,
            clientSecret = request.clientSecret,
            attributeMappings = request.attributeMappings,
            groupRoleMappings = request.groupRoleMappings,
            isMandatory = request.isMandatory
        )
        val saved = ssoConfigRepository.save(config)
        log.info("Created SSO config for org=$orgSlug provider=${request.provider}")
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    override fun getSsoConfig(orgSlug: String): SsoConfigResponse =
        (ssoConfigRepository.findByOrgSlug(orgSlug)
            ?: throw NotFoundException("SSO configuration not found for organisation '$orgSlug'"))
            .toResponse()

    override fun updateSsoConfig(orgSlug: String, request: UpdateSsoConfigRequest): SsoConfigResponse {
        val config = ssoConfigRepository.findByOrgSlug(orgSlug)
            ?: throw NotFoundException("SSO configuration not found for organisation '$orgSlug'")
        request.provider?.let { config.provider = it }
        request.issuerUrl?.let {
            val normalizedUrl = it.trimEnd('/')
            validateIssuerUrl(normalizedUrl)
            config.issuerUrl = normalizedUrl
        }
        request.clientId?.let { config.clientId = it }
        request.clientSecret?.let { config.clientSecret = it }
        request.attributeMappings?.let { config.attributeMappings = it }
        request.groupRoleMappings?.let { config.groupRoleMappings = it }
        request.isMandatory?.let { config.isMandatory = it }
        config.updatedAt = Instant.now()
        val saved = ssoConfigRepository.save(config)
        log.info("Updated SSO config for org=$orgSlug")
        return saved.toResponse()
    }

    override fun deleteSsoConfig(orgSlug: String) {
        val config = ssoConfigRepository.findByOrgSlug(orgSlug)
            ?: throw NotFoundException("SSO configuration not found for organisation '$orgSlug'")
        ssoConfigRepository.delete(config)
        log.info("Deleted SSO config for org=$orgSlug")
    }

    @Transactional(readOnly = true)
    override fun isSsoMandatory(orgSlug: String): Boolean =
        ssoConfigRepository.findByOrgSlug(orgSlug)?.isMandatory == true

    // -------------------------------------------------------------------------
    // Test Connection
    // -------------------------------------------------------------------------

    override fun testSsoConnection(orgSlug: String): SsoTestConnectionResponse {
        val config = ssoConfigRepository.findByOrgSlug(orgSlug)
            ?: throw NotFoundException("SSO configuration not found for organisation '$orgSlug'")
        return try {
            val discovery = fetchOidcDiscovery(config.issuerUrl)
            SsoTestConnectionResponse(
                success = true,
                message = "Successfully reached OIDC discovery endpoint",
                authorizationEndpoint = discovery["authorization_endpoint"] as? String,
                tokenEndpoint = discovery["token_endpoint"] as? String
            )
        } catch (ex: Exception) {
            log.warn("SSO test connection failed for org=$orgSlug: ${ex.message}")
            SsoTestConnectionResponse(
                success = false,
                message = "Unable to reach the OIDC discovery endpoint. Verify the issuer URL is correct and accessible."
            )
        }
    }

    // -------------------------------------------------------------------------
    // OIDC Login Flow
    // -------------------------------------------------------------------------

    override fun initiateSsoLogin(orgSlug: String, redirectUri: String): SsoLoginUrlResponse {
        val config = ssoConfigRepository.findByOrgSlug(orgSlug)
            ?: throw NotFoundException("SSO configuration not found for organisation '$orgSlug'")
        val discovery = fetchOidcDiscovery(config.issuerUrl)
        val authorizationEndpoint = discovery["authorization_endpoint"] as? String
            ?: throw BadRequestException("OIDC discovery missing authorization_endpoint")

        // Purge any expired state entries to prevent unbounded map growth.
        val now = Instant.now()
        pendingStates.entries.removeIf { (_, v) -> now.isAfter(v.expiresAt) }

        val state = randomUrlSafe(32)
        val nonce = randomUrlSafe(32)
        val codeVerifier = randomUrlSafe(48)
        // Store the redirect URI alongside the state so the callback can reuse it verbatim.
        pendingStates[state] = PendingOidcState(
            orgSlug = orgSlug,
            redirectUri = redirectUri,
            expiresAt = now.plusSeconds(600),
            nonce = nonce,
            codeVerifier = codeVerifier
        )

        val params = mapOf(
            "response_type" to "code",
            "client_id" to config.clientId,
            "redirect_uri" to redirectUri,
            "scope" to "openid profile email",
            "state" to state,
            "nonce" to nonce,
            "code_challenge" to codeChallengeFor(codeVerifier),
            "code_challenge_method" to "S256"
        )
        val query = params.entries.joinToString("&") { (k, v) ->
            "${encode(k)}=${encode(v)}"
        }
        val loginUrl = "$authorizationEndpoint?$query"
        log.info("Initiated SSO login for org=$orgSlug provider=${config.provider}")
        return SsoLoginUrlResponse(loginUrl = loginUrl, state = state)
    }

    override fun handleSsoCallback(
        orgSlug: String,
        code: String,
        state: String
    ): SsoCallbackResponse {
        // Validate state and recover the redirect URI that was used for the authorization request.
        val pendingState = pendingStates.remove(state)
            ?: throw BadRequestException("Invalid or expired SSO state parameter")
        if (pendingState.orgSlug != orgSlug) {
            throw BadRequestException("SSO state does not match organisation")
        }
        if (Instant.now().isAfter(pendingState.expiresAt)) {
            throw BadRequestException("SSO state has expired; please restart the login flow")
        }

        val config = ssoConfigRepository.findByOrgSlug(orgSlug)
            ?: throw NotFoundException("SSO configuration not found for organisation '$orgSlug'")

        val discovery = fetchOidcDiscovery(config.issuerUrl)
        val tokenEndpoint = discovery["token_endpoint"] as? String
            ?: throw BadRequestException("OIDC discovery missing token_endpoint")
        val jwksUri = discovery["jwks_uri"] as? String
            ?: throw BadRequestException("OIDC discovery missing jwks_uri; the ID token cannot be verified")
        val discoveredIssuer = discovery["issuer"] as? String ?: config.issuerUrl

        // Exchange authorization code for tokens using the same redirect URI as the
        // initial authorization request — OIDC requires an exact match — and the PKCE
        // verifier bound to this attempt.
        val tokenResponse = exchangeCodeForTokens(
            tokenEndpoint = tokenEndpoint,
            clientId = config.clientId,
            clientSecret = config.clientSecret,
            code = code,
            redirectUri = pendingState.redirectUri,
            codeVerifier = pendingState.codeVerifier
        )

        val idToken = tokenResponse["id_token"] as? String
            ?: throw BadRequestException("OIDC token response missing id_token")

        // Verify the token cryptographically before believing a single claim in it: signature
        // against the provider's JWKS, plus iss, aud, exp, nbf and the nonce bound to this
        // login attempt (#156 FR-2). This is the authentication decision.
        val claims = oidcTokenVerifier.verify(
            idToken = idToken,
            issuer = discoveredIssuer,
            jwksUri = jwksUri,
            clientId = config.clientId,
            expectedNonce = pendingState.nonce
        )

        val attrMappings: Map<String, String> = try {
            objectMapper.readValue(config.attributeMappings)
        } catch (ex: Exception) {
            log.warn("Failed to parse attribute_mappings for org=$orgSlug, falling back to defaults: ${ex.message}")
            mapOf("email" to "email", "name" to "name")
        }

        val email = claims[attrMappings.getOrDefault("email", "email")] as? String
            ?: throw BadRequestException("ID token missing email claim")
        val name = claims[attrMappings.getOrDefault("name", "name")] as? String ?: email

        // JIT user provisioning — create the user account on first SSO login.
        val user = userRepository.findByEmail(email)
            ?: userRepository.save(User(email = email, name = name))

        // Role mapping from IdP groups
        val groupsClaimKey = attrMappings["role"] ?: "groups"
        @Suppress("UNCHECKED_CAST")
        val groups = claims[groupsClaimKey] as? List<String> ?: emptyList()
        val role = resolveRole(groups, config.groupRoleMappings)

        // Create or synchronise org membership — update role on every SSO login so that
        // IdP group changes take effect without manual intervention.
        val existingMembership = membershipRepository.findByOrgSlugAndUserId(orgSlug, user.id)
        if (existingMembership == null) {
            membershipRepository.save(OrganisationMembership(orgSlug = orgSlug, userId = user.id, role = role))
        } else if (existingMembership.role != role) {
            existingMembership.role = role
            membershipRepository.save(existingMembership)
        }

        log.info("SSO callback: JIT provisioned/found user=${user.id} email=$email org=$orgSlug role=$role")

        val issued = sessionService.issue(
            userId = user.id,
            orgSlug = orgSlug,
            provider = AuthProvider.OIDC
        )
        return SsoCallbackResponse(
            token = issued.token,
            userId = user.id,
            email = email,
            name = name,
            orgSlug = orgSlug,
            role = role,
            expiresAt = issued.expiresAt
        )
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Lightweight save-time check: ensures the issuer URL uses HTTPS.
     * Full SSRF protection (private-range / loopback blocking) is applied in
     * [fetchOidcDiscovery] and [exchangeCodeForTokens] before any outbound call is made.
     */
    private fun validateIssuerUrl(url: String) {
        try {
            val uri = java.net.URI(url)
            if (uri.scheme?.lowercase() != "https") {
                throw BadRequestException("SSO issuer URL must use HTTPS (got scheme '${uri.scheme}')")
            }
        } catch (ex: BadRequestException) {
            throw ex
        } catch (ex: Exception) {
            throw BadRequestException("Invalid issuer URL: ${ex.message}")
        }
    }

    private fun fetchOidcDiscovery(issuerUrl: String): Map<String, Any> {
        // Full SSRF guard: reject private/loopback targets before making the request.
        try {
            UrlValidator.validate(issuerUrl)
        } catch (ex: IllegalArgumentException) {
            throw BadRequestException("Invalid issuer URL: ${ex.message}")
        }
        val discoveryUrl = "$issuerUrl/.well-known/openid-configuration"
        @Suppress("UNCHECKED_CAST")
        return restTemplate.getForObject(discoveryUrl, Map::class.java) as? Map<String, Any>
            ?: throw BadRequestException("Empty or invalid OIDC discovery document at $discoveryUrl")
    }

    private fun exchangeCodeForTokens(
        tokenEndpoint: String,
        clientId: String,
        clientSecret: String?,
        code: String,
        redirectUri: String,
        codeVerifier: String
    ): Map<String, Any> {
        // Validate the token endpoint from the discovery doc before calling it.
        try {
            UrlValidator.validate(tokenEndpoint)
        } catch (ex: IllegalArgumentException) {
            throw BadRequestException("OIDC token endpoint is not a safe HTTPS URL: ${ex.message}")
        }
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_FORM_URLENCODED }
        val body = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "authorization_code")
            add("client_id", clientId)
            clientSecret?.let { add("client_secret", it) }
            add("code", code)
            add("redirect_uri", redirectUri)
            if (codeVerifier.isNotBlank()) add("code_verifier", codeVerifier)
        }
        @Suppress("UNCHECKED_CAST")
        return restTemplate.postForObject(
            tokenEndpoint,
            HttpEntity(body, headers),
            Map::class.java
        ) as? Map<String, Any>
            ?: throw BadRequestException("Empty or invalid token endpoint response")
    }

    private fun randomUrlSafe(bytes: Int): String {
        val buffer = ByteArray(bytes)
        secureRandom.nextBytes(buffer)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer)
    }

    /** S256 PKCE challenge: BASE64URL(SHA256(verifier)). */
    private fun codeChallengeFor(verifier: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(StandardCharsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    /**
     * Resolves the [MemberRole] for a user based on their IdP groups and the organisation's
     * [groupRoleMappingsJson].
     *
     * When the user belongs to multiple mapped groups, the **highest-privilege** role wins
     * (ADMIN > MEMBER > VIEWER > SERVICE_ACCOUNT), giving deterministic results regardless of
     * the order in which the IdP returns group claims.  Falls back to [MemberRole.MEMBER] when
     * no groups match or the mappings JSON is invalid, this falls back to the **safe** default
     * (`VIEWER`), not to `MEMBER`: a federated user in no mapped group must not receive write
     * access just for having signed in (#156 FR-5.3).
     */
    internal fun resolveRole(groups: List<String>, groupRoleMappingsJson: String): MemberRole {
        if (groups.isEmpty()) return RoleModel.DEFAULT_ROLE_FOR_NEW_MEMBER
        val mappings: Map<String, String> = try {
            objectMapper.readValue(groupRoleMappingsJson)
        } catch (ex: Exception) {
            log.warn("Failed to parse group_role_mappings, falling back to MEMBER role: ${ex.message}")
            return RoleModel.DEFAULT_ROLE_FOR_NEW_MEMBER
        }
        val resolvedRoles = groups.mapNotNull { group ->
            val roleName = mappings[group] ?: return@mapNotNull null
            try { MemberRole.valueOf(roleName) } catch (_: IllegalArgumentException) { null }
        }.toSet()
        if (resolvedRoles.isEmpty()) return RoleModel.DEFAULT_ROLE_FOR_NEW_MEMBER
        return ROLE_PRIVILEGE_ORDER.first { it in resolvedRoles }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private fun SsoConfig.toResponse() = SsoConfigResponse(
        id = id,
        orgSlug = orgSlug,
        provider = provider,
        issuerUrl = issuerUrl,
        clientId = clientId,
        attributeMappings = attributeMappings,
        groupRoleMappings = groupRoleMappings,
        isMandatory = isMandatory,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
