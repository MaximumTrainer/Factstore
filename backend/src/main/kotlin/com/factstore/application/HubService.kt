package com.factstore.application

import com.factstore.core.domain.HubTemplate
import com.factstore.core.domain.OrgTemplate
import com.factstore.core.domain.TemplateCategory
import com.factstore.core.port.outbound.IOrgTemplateRepository
import com.factstore.dto.ComposeTemplateRequest
import com.factstore.dto.ComposedTemplateResponse
import com.factstore.exception.BadRequestException
import com.factstore.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.stereotype.Service
import org.yaml.snakeyaml.Yaml

@Service
class HubService(
    private val orgTemplateRepository: IOrgTemplateRepository,
    private val templateComposer: TemplateComposer
) {
    private val log = LoggerFactory.getLogger(HubService::class.java)

    private val builtIns: Map<String, HubTemplate> by lazy { loadTemplates() }

    /**
     * Built-in templates plus any the organisation has published itself. An org template with the
     * same id as a built-in shadows it, so a platform team can override the house standard.
     */
    fun listTemplates(category: TemplateCategory? = null, orgSlug: String? = null): List<HubTemplate> {
        val custom = orgTemplateRepository.findAll()
            .filter { orgSlug == null || it.orgSlug == orgSlug }
            .map { it.toHubTemplate() }
        val merged = (builtIns.values + custom).associateBy { it.id }.values
        return merged.filter { category == null || it.category == category }.sortedBy { it.id }
    }

    fun getTemplate(id: String, orgSlug: String? = null): HubTemplate =
        listTemplates(orgSlug = orgSlug).firstOrNull { it.id == id }
            ?: throw NotFoundException("Hub template not found: $id")

    /**
     * Combines several templates into one, so a flow can be defined as, say,
     * `service-public-api` + `pci-dss-v4`. Returns the union of the required attestations and
     * surfaces any name required with two different types rather than silently picking one.
     */
    fun compose(request: ComposeTemplateRequest): ComposedTemplateResponse {
        if (request.templateIds.isEmpty()) {
            throw BadRequestException("At least one template id is required")
        }
        val templates = request.templateIds.map { getTemplate(it, request.orgSlug) }
        log.debug("Composing templates: {}", request.templateIds)
        return templateComposer.compose(templates)
    }

    private fun OrgTemplate.toHubTemplate() = HubTemplate(
        id = templateId,
        name = name,
        description = description,
        framework = framework,
        version = version,
        yaml = templateYaml,
        category = category,
        serviceType = serviceType,
        orgSlug = orgSlug
    )

    private fun loadTemplates(): Map<String, HubTemplate> {
        val resolver = PathMatchingResourcePatternResolver()
        val resources = resolver.getResources("classpath*:hub-templates/*.yml")
        val yaml = Yaml()
        return resources.mapNotNull { resource ->
            try {
                val content = resource.inputStream.bufferedReader().readText()
                @Suppress("UNCHECKED_CAST")
                val map = yaml.load<Map<String, Any>>(content)
                HubTemplate(
                    id = map["id"] as String,
                    name = map["name"] as String,
                    description = map["description"] as String,
                    framework = map["framework"] as String,
                    version = map["version"].toString(),
                    yaml = content,
                    category = parseCategory(map["category"]),
                    serviceType = map["serviceType"] as? String
                )
            } catch (e: Exception) {
                log.warn("Skipping unreadable hub template ${resource.filename}: ${e.message}")
                null
            }
        }.associateBy { it.id }
    }

    private fun parseCategory(raw: Any?): TemplateCategory = when (raw?.toString()?.lowercase()) {
        "service-type", "service_type" -> TemplateCategory.SERVICE_TYPE
        // Everything that predates the split is a regulatory framework template.
        else -> TemplateCategory.FRAMEWORK
    }
}
