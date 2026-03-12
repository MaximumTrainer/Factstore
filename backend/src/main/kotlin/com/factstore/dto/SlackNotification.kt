package com.factstore.dto

import java.util.UUID

sealed class SlackNotification {
    data class TrailNonCompliant(
        val trailId: UUID,
        val flowName: String,
        val missingAttestationTypes: List<String>,
        val failedAttestationTypes: List<String>,
        val trailUrl: String? = null
    ) : SlackNotification()

    data class ApprovalRequested(
        val approvalId: String,
        val artifactSha: String,
        val targetEnvironment: String,
        val requiredApprovers: List<String>
    ) : SlackNotification()

    data class ApprovalDecision(
        val approvalId: String,
        val decision: String,
        val decidedBy: String,
        val comment: String? = null
    ) : SlackNotification()
}
