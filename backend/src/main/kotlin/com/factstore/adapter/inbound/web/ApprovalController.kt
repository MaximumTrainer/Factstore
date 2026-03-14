package com.factstore.adapter.inbound.web

import com.factstore.core.domain.ApprovalStatus
import com.factstore.core.port.inbound.IApprovalService
import com.factstore.dto.ApprovalResponse
import com.factstore.dto.ApproveRequest
import com.factstore.dto.CreateApprovalRequest
import com.factstore.dto.RejectRequest
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@Tag(name = "Approvals", description = "Release approval workflow")
class ApprovalController(private val approvalService: IApprovalService) {

    @PostMapping("/api/v1/trails/{trailId}/approvals")
    fun requestApproval(
        @PathVariable trailId: UUID,
        @RequestBody request: CreateApprovalRequest
    ): ResponseEntity<ApprovalResponse> =
        ResponseEntity.ok(approvalService.requestApproval(request.copy(trailId = trailId)))

    @GetMapping("/api/v1/trails/{trailId}/approvals")
    fun listApprovalsByTrail(@PathVariable trailId: UUID): ResponseEntity<List<ApprovalResponse>> =
        ResponseEntity.ok(approvalService.listApprovalsByTrail(trailId))

    @GetMapping("/api/v1/approvals/{approvalId}")
    fun getApproval(@PathVariable approvalId: UUID): ResponseEntity<ApprovalResponse> =
        ResponseEntity.ok(approvalService.getApproval(approvalId))

    @PostMapping("/api/v1/approvals/{approvalId}/approve")
    fun approve(
        @PathVariable approvalId: UUID,
        @RequestBody request: ApproveRequest
    ): ResponseEntity<ApprovalResponse> =
        ResponseEntity.ok(approvalService.approve(approvalId, request))

    @PostMapping("/api/v1/approvals/{approvalId}/reject")
    fun reject(
        @PathVariable approvalId: UUID,
        @RequestBody request: RejectRequest
    ): ResponseEntity<ApprovalResponse> =
        ResponseEntity.ok(approvalService.reject(approvalId, request))

    @GetMapping("/api/v1/approvals")
    fun listApprovals(@RequestParam(required = false) status: ApprovalStatus?): ResponseEntity<List<ApprovalResponse>> =
        ResponseEntity.ok(
            if (status != null) approvalService.listApprovalsByStatus(status)
            else approvalService.listAll()
        )
}
