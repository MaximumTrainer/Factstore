package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class ApprovalStatus { PENDING_APPROVAL, APPROVED, REJECTED, EXPIRED }

@Entity
@Table(name = "approvals")
class Approval(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "trail_id", nullable = false) val trailId: UUID,
    @Column(name = "flow_id", nullable = false) val flowId: UUID,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: ApprovalStatus = ApprovalStatus.PENDING_APPROVAL,
    @Column(name = "required_approvers", columnDefinition = "TEXT") var requiredApproversRaw: String = "",
    @Column(columnDefinition = "TEXT") var comments: String? = null,
    @Column(name = "requested_at", nullable = false) val requestedAt: Instant = Instant.now(),
    @Column(name = "deadline") var deadline: Instant? = null,
    @Column(name = "resolved_at") var resolvedAt: Instant? = null
) {
    var requiredApprovers: List<String>
        get() = if (requiredApproversRaw.isBlank()) emptyList() else requiredApproversRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }
        set(value) { requiredApproversRaw = value.joinToString(",") }
}
