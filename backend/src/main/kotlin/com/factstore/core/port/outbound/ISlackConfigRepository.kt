package com.factstore.core.port.outbound

import com.factstore.core.domain.SlackConfig

interface ISlackConfigRepository {
    fun save(config: SlackConfig): SlackConfig
    fun findByOrgSlug(orgSlug: String): SlackConfig?
    fun deleteByOrgSlug(orgSlug: String)
    fun existsByOrgSlug(orgSlug: String): Boolean
}
