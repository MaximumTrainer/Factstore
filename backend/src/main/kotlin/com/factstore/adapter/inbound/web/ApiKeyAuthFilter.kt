package com.factstore.adapter.inbound.web

import com.factstore.core.port.inbound.IApiKeyService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Extracts an API key from the request and validates it against the stored BCrypt hashes,
 * then populates the [SecurityContextHolder] when the key is valid.
 *
 * Supported authentication schemes (checked in order):
 * 1. `X-API-Key: <key>` header
 * 2. `Authorization: Bearer <key>` (standard Bearer token)
 * 3. `Authorization: ApiKey <key>` (legacy scheme, kept for backward compatibility)
 *
 * The filter is non-blocking: missing or invalid keys do not abort the request —
 * route-level access rules in [com.factstore.config.SecurityConfig] determine whether
 * an unauthenticated request is accepted.
 */
@Component
class ApiKeyAuthFilter(private val apiKeyService: IApiKeyService) : OncePerRequestFilter() {

    companion object {
        private const val API_KEY_HEADER = "X-API-Key"
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
        private const val API_KEY_PREFIX = "ApiKey "
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val rawKey = extractKey(request)
        if (rawKey != null && SecurityContextHolder.getContext().authentication == null) {
            val apiKeyResponse = apiKeyService.validateApiKey(rawKey)
            if (apiKeyResponse != null) {
                val authorities = listOf(SimpleGrantedAuthority("ROLE_API_USER"))
                val auth = UsernamePasswordAuthenticationToken(
                    apiKeyResponse.ownerId.toString(),
                    null,
                    authorities
                )
                SecurityContextHolder.getContext().authentication = auth
            }
        }
        filterChain.doFilter(request, response)
    }

    private fun extractKey(request: HttpServletRequest): String? {
        val directHeader = request.getHeader(API_KEY_HEADER)
        if (!directHeader.isNullOrBlank()) return directHeader

        val authHeader = request.getHeader(AUTHORIZATION_HEADER)
        if (!authHeader.isNullOrBlank()) {
            if (authHeader.startsWith(BEARER_PREFIX)) {
                return authHeader.removePrefix(BEARER_PREFIX).trim()
            }
            if (authHeader.startsWith(API_KEY_PREFIX)) {
                return authHeader.removePrefix(API_KEY_PREFIX).trim()
            }
        }
        return null
    }
}
