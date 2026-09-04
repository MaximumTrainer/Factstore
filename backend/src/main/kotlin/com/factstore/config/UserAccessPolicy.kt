package com.factstore.config

import com.factstore.application.auth.AuthenticatedUser
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * The "or it's your own record" half of the user authorisation rules (#156 FR-5.5),
 * referenced from `@PreAuthorize` as `@userAccessPolicy.isSelf(#id)`.
 *
 * Kept as a bean rather than inlined into the SpEL expression so the rule is testable and
 * stated once, instead of being retyped in every annotation.
 */
@Component("userAccessPolicy")
class UserAccessPolicy {

    /** True when the authenticated principal *is* the user being addressed. */
    fun isSelf(userId: UUID?): Boolean {
        if (userId == null) return false
        val principal = SecurityContextHolder.getContext().authentication?.principal
        return (principal as? AuthenticatedUser)?.userId == userId
    }
}
