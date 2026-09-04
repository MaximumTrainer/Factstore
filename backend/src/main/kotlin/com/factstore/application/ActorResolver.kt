package com.factstore.application

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * Resolves the actor to attribute an audit event to.
 *
 * Reads whatever principal the authentication filters put on the security context — an API key
 * owner, an SSO user — falling back to `system` when the request is unauthenticated. Once #155
 * and #156 land, every request carries a credential and this returns a real identity.
 */
@Component
class ActorResolver {

    fun current(): String {
        val authentication = SecurityContextHolder.getContext().authentication
            ?: return SYSTEM
        val name = authentication.name
        return if (name.isNullOrBlank() || name == "anonymousUser") SYSTEM else name
    }

    companion object {
        const val SYSTEM = "system"
    }
}
