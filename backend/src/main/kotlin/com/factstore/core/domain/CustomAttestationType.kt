package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "custom_attestation_types")
class CustomAttestationType(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false, unique = true)
    var name: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    var description: String = "",

    @Column(nullable = false)
    var version: Int = 1,

    @Column(name = "org_slug", nullable = true, length = 255)
    var orgSlug: String? = null,

    @Column(name = "schema_json", columnDefinition = "TEXT")
    var schemaJson: String? = null,

    @Column(name = "jq_expression", columnDefinition = "TEXT")
    var jqExpression: String? = null,

    @Column(name = "archived_at", nullable = true)
    var archivedAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
