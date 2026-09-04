package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.OrgTemplate
import com.factstore.core.port.outbound.IOrgTemplateRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface OrgTemplateRepositoryJpa : JpaRepository<OrgTemplate, UUID> {
    fun findByOrgSlugAndTemplateId(orgSlug: String, templateId: String): OrgTemplate?

    @Query("SELECT t FROM OrgTemplate t WHERE t.orgSlug IS NULL AND t.templateId = :templateId")
    fun findGlobalByTemplateId(@Param("templateId") templateId: String): OrgTemplate?
}

@Component
class OrgTemplateRepositoryAdapter(private val jpa: OrgTemplateRepositoryJpa) : IOrgTemplateRepository {
    override fun save(template: OrgTemplate): OrgTemplate = jpa.save(template)
    override fun findById(id: UUID): OrgTemplate? = jpa.findById(id).orElse(null)
    override fun findAll(): List<OrgTemplate> = jpa.findAll()
    override fun findByOrgSlugAndTemplateId(orgSlug: String?, templateId: String): OrgTemplate? =
        if (orgSlug == null) jpa.findGlobalByTemplateId(templateId)
        else jpa.findByOrgSlugAndTemplateId(orgSlug, templateId)
    override fun deleteById(id: UUID) = jpa.deleteById(id)
}
