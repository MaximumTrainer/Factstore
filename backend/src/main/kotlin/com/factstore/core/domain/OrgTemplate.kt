package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * A flow template published by an organisation (#162), listed alongside the built-in catalogue.
 * An org template whose [templateId] matches a built-in shadows it, so a platform team can
 * override the house standard without forking the product.
 */
@Entity
@Table(name = "org_templates")
class OrgTemplate(
    @Id
    val id: UUID = UUID.randomUUID(),

    /** The stable id the template is addressed by, e.g. "service-public-api". */
    @Column(name = "template_id", nullable = false, length = 128)
    var templateId: String,

    @Column(name = "org_slug", nullable = true, length = 255)
    var orgSlug: String? = null,

    @Column(nullable = false)
    var name: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    var description: String,

    @Column(nullable = false, length = 128)
    var framework: String = "custom",

    @Column(nullable = false, length = 32)
    var version: String = "1.0",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var category: TemplateCategory = TemplateCategory.SERVICE_TYPE,

    @Column(name = "service_type", nullable = true, length = 64)
    var serviceType: String? = null,

    @Column(name = "template_yaml", nullable = false, columnDefinition = "TEXT")
    var templateYaml: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
