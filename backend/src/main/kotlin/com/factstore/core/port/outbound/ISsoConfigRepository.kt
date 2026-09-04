package com.factstore.core.port.outbound

import com.factstore.core.domain.SsoConfig

interface ISsoConfigRepository {
    fun save(ssoConfig: SsoConfig): SsoConfig
    fun findByOrgSlug(orgSlug: String): SsoConfig?
    fun existsByOrgSlug(orgSlug: String): Boolean
    /** Used at startup to decide whether interactive sign-in is reachable (#156 FR-3.1). */
    fun findAll(): List<SsoConfig>
    fun delete(ssoConfig: SsoConfig)
}
