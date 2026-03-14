package com.factstore.core.port.inbound

import com.factstore.core.domain.ApprovalStatus
import com.factstore.dto.ApprovalResponse
import com.factstore.dto.ApproveRequest
import com.factstore.dto.CreateApprovalRequest
import com.factstore.dto.RejectRequest
import java.util.UUID

interface IApprovalService {
    fun requestApproval(request: CreateApprovalRequest): ApprovalResponse
    fun getApproval(approvalId: UUID): ApprovalResponse
    fun listApprovalsByTrail(trailId: UUID): List<ApprovalResponse>
    fun listApprovalsByStatus(status: ApprovalStatus): List<ApprovalResponse>
    fun listAll(): List<ApprovalResponse>
    fun approve(approvalId: UUID, request: ApproveRequest): ApprovalResponse
    fun reject(approvalId: UUID, request: RejectRequest): ApprovalResponse
}
