package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class ApprovalDecisionType { APPROVED, REJECTED }

@Entity
@Table(name = "approval_decisions")
class ApprovalDecision(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "approval_id", nullable = false) val approvalId: UUID,
    @Column(name = "approver_identity", nullable = false) val approverIdentity: String,
    @Enumerated(EnumType.STRING) @Column(nullable = false) val decision: ApprovalDecisionType,
    @Column(columnDefinition = "TEXT") val comments: String? = null,
    @Column(name = "decided_at", nullable = false) val decidedAt: Instant = Instant.now()
)
