package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "policy_versions")
class PolicyVersion(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "policy_id", nullable = false)
    val policyId: UUID,

    @Column(nullable = false)
    val version: Int,

    @Column(nullable = false, columnDefinition = "TEXT")
    val content: String,

    @Column(name = "change_comment", columnDefinition = "TEXT")
    val changeComment: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
