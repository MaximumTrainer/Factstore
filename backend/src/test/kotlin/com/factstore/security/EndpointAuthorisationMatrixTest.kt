package com.factstore.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping

/**
 * Guards the authorisation matrix (#155 FR-4.1, FR-4.3).
 *
 * The defect class this exists to remove is a **silently-open endpoint**: an operation that
 * needs a privilege but carries no annotation, so it looks protected in review and is not.
 * A convention nobody checks decays, so it is checked here.
 *
 * The check is deliberately scoped to the operations #155's acceptance criteria name — flow
 * deletion, policy upload, member, key, service-account and SSO management — rather than the
 * whole surface. Annotating all ~55 controllers is the remaining work, and a test that fails
 * for 200 known-unannotated endpoints would be turned off within a day. As endpoints are
 * annotated, add their controller to [required].
 */
@SpringBootTest
class EndpointAuthorisationMatrixTest {

    // Actuator registers a second RequestMappingHandlerMapping, so name the MVC one.
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    lateinit var handlerMapping: RequestMappingHandlerMapping

    /** Controller simple name -> the scope its mutating endpoints must require. */
    private val required = mapOf(
        "FlowController" to "SCOPE_flows:write",
        "PolicyController" to "SCOPE_policies:write",
        "OrganisationMemberController" to "SCOPE_admin",
        "ServiceAccountController" to "SCOPE_admin",
        "SsoController" to "SCOPE_admin",
        "ApiKeyController" to "SCOPE_admin"
    )

    /**
     * Endpoints on those controllers that are intentionally not gated, with the reason.
     * Anything not listed here must be annotated.
     */
    private val allowedUngated = mapOf(
        // Sign-in cannot require being signed in.
        "SsoController#initiateSsoLogin" to "sign-in entry point",
        "SsoController#handleSsoCallback" to "sign-in callback",
        // Reads, which the route rules and tenant scoping cover.
        "FlowController#listFlows" to "read",
        "FlowController#getFlow" to "read",
        "FlowController#getFlowTemplate" to "read",
        "FlowController#getFlowImpact" to "read",
        "FlowController#getTemplateDrift" to "read",
        "PolicyController#listPolicies" to "read",
        "PolicyController#getPolicy" to "read",
        "PolicyController#listPolicyVersions" to "read",
        "OrganisationMemberController#listMembers" to "read",
        "OrganisationMemberController#getMember" to "read",
        "ServiceAccountController#listServiceAccounts" to "read",
        "ServiceAccountController#getServiceAccount" to "read",
        "ServiceAccountController#listApiKeys" to "read",
        "SsoController#getSsoConfig" to "read",
        // Public metadata, and self-service reads guarded by their own expression.
        "ApiKeyController#listScopes" to "static vocabulary",
        "ApiKeyController#listApiKeysForOwner" to "guarded by an ownership expression"
    )

    private data class Endpoint(val controller: String, val method: String, val handler: HandlerMethod)

    private fun endpointsFor(controller: String): List<Endpoint> =
        handlerMapping.handlerMethods.values
            .filter { it.beanType.simpleName == controller }
            .map { Endpoint(controller, it.method.name, it) }
            .distinctBy { "${it.controller}#${it.method}" }

    private fun authorisationOn(handler: HandlerMethod): String? =
        handler.getMethodAnnotation(PreAuthorize::class.java)?.value
            ?: handler.beanType.getAnnotation(PreAuthorize::class.java)?.value

    @Test
    fun `every controller in the matrix is actually registered`() {
        required.keys.forEach { controller ->
            assertTrue(
                endpointsFor(controller).isNotEmpty(),
                "$controller has no registered endpoints — has it been renamed? The matrix would " +
                    "then be silently passing."
            )
        }
    }

    @Test
    fun `no privileged endpoint is left without an authorisation annotation`() {
        val unannotated = mutableListOf<String>()

        required.forEach { (controller, _) ->
            endpointsFor(controller).forEach { endpoint ->
                val key = "${endpoint.controller}#${endpoint.method}"
                if (allowedUngated.containsKey(key)) return@forEach
                if (authorisationOn(endpoint.handler) == null) unannotated += key
            }
        }

        assertTrue(
            unannotated.isEmpty(),
            "These endpoints need an @PreAuthorize, or an entry in allowedUngated saying why " +
                "not: ${unannotated.sorted()}"
        )
    }

    @Test
    fun `each privileged endpoint requires the scope the matrix says it should`() {
        val wrong = mutableListOf<String>()

        required.forEach { (controller, expectedScope) ->
            endpointsFor(controller).forEach { endpoint ->
                val key = "${endpoint.controller}#${endpoint.method}"
                if (allowedUngated.containsKey(key)) return@forEach
                val expression = authorisationOn(endpoint.handler) ?: return@forEach
                if (!expression.contains(expectedScope)) {
                    wrong += "$key requires '$expression', expected $expectedScope"
                }
            }
        }

        assertTrue(wrong.isEmpty(), "Authorisation does not match the documented matrix: $wrong")
    }

    @Test
    fun `every deletion in the matrix is gated`() {
        val ungatedDeletes = mutableListOf<String>()

        required.keys.forEach { controller ->
            endpointsFor(controller)
                .filter { it.handler.getMethodAnnotation(DeleteMapping::class.java) != null }
                .forEach { endpoint ->
                    if (authorisationOn(endpoint.handler) == null) {
                        ungatedDeletes += "${endpoint.controller}#${endpoint.method}"
                    }
                }
        }

        assertTrue(ungatedDeletes.isEmpty(), "Ungated deletion endpoints: $ungatedDeletes")
    }

    @Test
    fun `the allowlist does not name endpoints that no longer exist`() {
        val live = required.keys.flatMap { endpointsFor(it) }
            .map { "${it.controller}#${it.method}" }
            .toSet()

        val stale = allowedUngated.keys.filterNot { it in live }

        // A stale allowlist entry is how an exemption outlives the endpoint it was written
        // for and starts silently covering a new one.
        assertEquals(emptyList<String>(), stale.sorted(), "Stale allowedUngated entries")
    }

    @Test
    fun `the matrix covers the endpoints named in the acceptance criteria`() {
        val flowDelete = endpointsFor("FlowController").single { it.method == "deleteFlow" }
        val policyCreate = endpointsFor("PolicyController").firstOrNull { it.method.startsWith("create") }
        val keyCreate = endpointsFor("ApiKeyController").single { it.method == "createApiKey" }

        assertTrue(authorisationOn(flowDelete.handler)!!.contains("SCOPE_flows:write"))
        assertTrue(authorisationOn(keyCreate.handler)!!.contains("SCOPE_admin"))
        policyCreate?.let {
            assertTrue(authorisationOn(it.handler)!!.contains("SCOPE_policies:write"))
        }
    }

    @Test
    fun `controllers are addressed under a versioned api path`() {
        required.keys.forEach { controller ->
            val paths = handlerMapping.handlerMethods
                .filter { (_, handler) -> handler.beanType.simpleName == controller }
                .keys
                .flatMap { it.pathPatternsCondition?.patternValues ?: emptySet() }
            assertTrue(
                paths.isNotEmpty() && paths.all { it.startsWith("/api/v") },
                "$controller serves non-API paths: $paths"
            )
        }
    }
}
