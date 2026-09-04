package com.factstore.core.domain

/**
 * Two kinds of template, which answer different questions (#162):
 *  - SERVICE_TYPE — "what does an organisation expect of a service of this shape?"
 *  - FRAMEWORK    — "what does this regulation or standard require?"
 *
 * A flow commonly wants both, which is what [com.factstore.application.HubService.compose] is for.
 */
enum class TemplateCategory { SERVICE_TYPE, FRAMEWORK }

data class HubTemplate(
    val id: String,
    val name: String,
    val description: String,
    val framework: String,
    val version: String,
    val yaml: String,
    val category: TemplateCategory = TemplateCategory.FRAMEWORK,
    /** Set on service-type templates: the shape of service the template is the baseline for. */
    val serviceType: String? = null,
    /** Null for built-ins; set for templates an organisation has published itself. */
    val orgSlug: String? = null
)
