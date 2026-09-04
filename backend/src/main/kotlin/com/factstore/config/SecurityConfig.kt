package com.factstore.config

import com.factstore.adapter.inbound.web.ApiKeyAuthFilter
import com.factstore.adapter.inbound.web.SessionAuthFilter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

/**
 * Central security configuration for OpenFactstore.
 *
 * Access model:
 *  - REST API callers authenticate via API keys (header `X-API-Key` or `Authorization: ApiKey <key>`).
 *  - People authenticate with a session issued by `/sso/callback`, presented as the
 *    `fs_session` HttpOnly cookie (web UI) or `Authorization: Bearer <token>` (API clients).
 *    See [SessionAuthFilter] and `docs/authentication.md`.
 *  - Web UI users authenticate via GitHub OAuth 2.0 / SSO (enabled when
 *    `GITHUB_CLIENT_ID` and `GITHUB_CLIENT_SECRET` environment variables are set).
 *  - Public paths (Swagger UI, H2 console, OpenAPI docs) are permitted without authentication.
 *
 * HTTPS enforcement: configure `server.ssl.*` properties and set
 * `server.ssl.enabled=true` in your production `application.yml` (or via environment
 * variables). All traffic must be over TLS in production deployments.
 *
 * The [BCryptPasswordEncoder] bean lives in [PasswordEncoderConfig] to avoid a circular
 * dependency with [ApiKeyAuthFilter].
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val apiKeyAuthFilter: ApiKeyAuthFilter,
    private val sessionAuthFilter: SessionAuthFilter,
    @Value("\${spring.security.oauth2.client.registration.github.client-id:}")
    private val githubClientId: String,
    @Value("\${security.enforce-auth:false}")
    private val enforceAuth: Boolean
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { csrf -> csrf.ignoringRequestMatchers("/api/v1/**", "/api/v2/**") }
            // Allow H2 console frames (dev only)
            .headers { headers -> headers.frameOptions { it.sameOrigin() } }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED) }
            .authorizeHttpRequests { auth ->
                auth
                    // H2 console (dev)
                    .requestMatchers("/h2-console/**").permitAll()
                    // OpenAPI / Swagger
                    .requestMatchers("/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                    // OAuth2 login endpoints
                    .requestMatchers("/login/**", "/oauth2/**").permitAll()
                    // Actuator / health endpoints are always public
                    .requestMatchers("/actuator/**", "/health").permitAll()
                    // Sign-in itself cannot require being signed in.
                    .requestMatchers("/api/v1/organisations/*/sso/login", "/api/v1/organisations/*/sso/callback").permitAll()
                    // Logout must work with an expired or already-revoked session, so the
                    // cookie can always be cleared.
                    .requestMatchers("/api/v1/auth/logout").permitAll()
                    // Remaining endpoints: enforce authentication when the flag is enabled.
                    // Set SECURITY_ENFORCE_AUTH=true in production to require authentication.
                if (enforceAuth) {
                    auth.anyRequest().authenticated()
                } else {
                    auth.anyRequest().permitAll()
                }
            }
            // The session filter runs first so a signed-in person is recognised before the
            // API key filter tries to read their Bearer token as an API key.
            .addFilterBefore(sessionAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterAfter(apiKeyAuthFilter, SessionAuthFilter::class.java)

        // Enable GitHub OAuth2 login only when credentials are configured
        if (githubClientId.isNotBlank()) {
            http.oauth2Login { }
        }

        return http.build()
    }
}
