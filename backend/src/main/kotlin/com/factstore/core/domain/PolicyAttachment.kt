package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "policy_attachments",
    uniqueConstraints = [UniqueConstraint(name = "uq_policy_attachments_policy_env", columnNames = ["policy_id", "environment_id"])]
)
class PolicyAttachment(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "policy_id", nullable = false)
    val policyId: UUID,

    @Column(name = "environment_id", nullable = false)
    val environmentId: UUID,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
