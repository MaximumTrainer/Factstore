package com.factstore.core.port.outbound

import com.factstore.core.domain.ApprovalDecision
import java.util.UUID

interface IApprovalDecisionRepository {
    fun save(decision: ApprovalDecision): ApprovalDecision
    fun findByApprovalId(approvalId: UUID): List<ApprovalDecision>
}
