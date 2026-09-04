package com.factstore.core.port.outbound

import com.factstore.core.domain.OrgTemplate
import java.util.UUID

interface IOrgTemplateRepository {
    fun save(template: OrgTemplate): OrgTemplate
    fun findById(id: UUID): OrgTemplate?
    fun findAll(): List<OrgTemplate>
    fun findByOrgSlugAndTemplateId(orgSlug: String?, templateId: String): OrgTemplate?
    fun deleteById(id: UUID)
}
