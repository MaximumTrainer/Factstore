package com.factstore.config

import com.factstore.application.auth.SessionTokenService
import com.factstore.core.port.outbound.ISsoConfigRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Refuses to run with an unusable session signing secret (#156 FR-3.1).
 *
 * The previous behaviour was a startup `WARN` while continuing to run on
 * `changeme-in-production`. A warning is not a control: anyone who knew the default could forge
 * a session token for any user, and the deployment would look healthy. So when sign-in is
 * actually reachable, an unset, placeholder or too-short secret is a startup failure.
 *
 * "Sign-in is reachable" means either SSO is configured for an organisation or
 * `security.enforce-auth` is on. A local development instance with neither keeps working
 * without a secret, and cannot issue sessions either — which is the honest position.
 */
@Component
class SessionSecretValidator(
    private val tokenService: SessionTokenService,
    private val ssoConfigRepository: ISsoConfigRepository,
    @Value("\${security.enforce-auth:false}") private val enforceAuth: Boolean,
    @Value("\${security.session.allow-unconfigured-secret:false}") private val allowUnconfigured: Boolean
) {

    private val log = LoggerFactory.getLogger(SessionSecretValidator::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun validate() {
        if (tokenService.isConfigured()) {
            log.info("Session signing secret configured")
            return
        }

        val problem = tokenService.secretProblem() ?: "session signing secret is unusable"
        val ssoConfigured = runCatching { ssoConfigRepository.findAll().isNotEmpty() }
            .getOrElse { false }

        if (!ssoConfigured && !enforceAuth) {
            log.warn(
                "No session signing secret is configured, so interactive sign-in is unavailable. " +
                    "This is fine for local development. Set SSO_JWT_SECRET before configuring SSO " +
                    "or enabling SECURITY_ENFORCE_AUTH. ($problem)"
            )
            return
        }

        if (allowUnconfigured) {
            log.error(
                "Running with an unusable session signing secret because " +
                    "security.session.allow-unconfigured-secret=true. Sessions issued by this " +
                    "instance cannot be trusted. ($problem)"
            )
            return
        }

        // Fail fast rather than start an instance whose sessions can be forged.
        throw IllegalStateException(
            "Refusing to start: $problem " +
                "SSO is configured or authentication is enforced, so sessions must be signed with a " +
                "real secret. Set SSO_JWT_SECRET, or set " +
                "security.session.allow-unconfigured-secret=true to override (not for production)."
        )
    }
}
