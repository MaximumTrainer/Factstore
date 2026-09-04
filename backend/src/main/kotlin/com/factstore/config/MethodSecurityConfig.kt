package com.factstore.config

import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity

/**
 * Method security is **always** enabled (#155 FR-1.5).
 *
 * It used to be conditional on `security.enforce-auth=true`, which meant every
 * `@PreAuthorize` in the codebase was inert in the default configuration — an authorisation
 * rule that does nothing unless a flag is set is not an authorisation rule, and its presence
 * in the source makes the system look protected when it is not.
 *
 * With enforcement off, requests still arrive unauthenticated and route rules still permit
 * them; what changes is that a `@PreAuthorize` on a method is now actually evaluated, so an
 * annotated endpoint refuses an unauthenticated caller even in a permissive deployment. That
 * is the intended behaviour: the annotations mark operations that need a real principal.
 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true)
class MethodSecurityConfig
