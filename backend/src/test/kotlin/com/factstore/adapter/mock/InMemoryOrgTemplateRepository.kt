package com.factstore.adapter.mock

import com.factstore.core.domain.OrgTemplate
import com.factstore.core.port.outbound.IOrgTemplateRepository
import java.util.UUID

/** In-memory [IOrgTemplateRepository] for unit tests that run without a Spring context. */
class InMemoryOrgTemplateRepository : IOrgTemplateRepository {
    private val store = mutableMapOf<UUID, OrgTemplate>()

    override fun save(template: OrgTemplate): OrgTemplate {
        store[template.id] = template
        return template
    }

    override fun findById(id: UUID): OrgTemplate? = store[id]

    override fun findAll(): List<OrgTemplate> = store.values.toList()

    override fun findByOrgSlugAndTemplateId(orgSlug: String?, templateId: String): OrgTemplate? =
        store.values.firstOrNull { it.orgSlug == orgSlug && it.templateId == templateId }

    override fun deleteById(id: UUID) {
        store.remove(id)
    }
}
