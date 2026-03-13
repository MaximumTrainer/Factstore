package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * A service account represents a machine identity designed for programmatic
 * access from CI/CD pipelines or other external systems.
 *
 * API keys for a service account are linked via [ApiKey.ownerId] with
 * [ApiKey.ownerType] set to [OwnerType.SERVICE_ACCOUNT].
 */
@Entity
@Table(name = "service_accounts")
class ServiceAccount(
    @Id
    val id: UUID = UUID.randomUUID(),

    /** Unique, human-readable identifier (e.g. "backend-ci-runner"). */
    @Column(nullable = false, unique = true)
    var name: String,

    @Column
    var description: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
