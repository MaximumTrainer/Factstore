package com.factstore

import com.factstore.application.ApprovalService
import com.factstore.application.FlowService
import com.factstore.application.TrailService
import com.factstore.core.domain.ApprovalStatus
import com.factstore.dto.*
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@Transactional
class ApprovalServiceTest {

    @Autowired lateinit var approvalService: ApprovalService
    @Autowired lateinit var flowService: FlowService
    @Autowired lateinit var trailService: TrailService

    private fun createTrail(): TrailResponse {
        val flow = flowService.createFlow(CreateFlowRequest(name = "approval-test-flow-${UUID.randomUUID()}", description = "desc"))
        return trailService.createTrail(CreateTrailRequest(
            flowId = flow.id,
            gitCommitSha = "abc123",
            gitBranch = "main",
            gitAuthor = "Test User",
            gitAuthorEmail = "test@example.com"
        ))
    }

    @Test
    fun `requestApproval creates approval in PENDING_APPROVAL state`() {
        val trail = createTrail()
        val response = approvalService.requestApproval(CreateApprovalRequest(trailId = trail.id))
        assertEquals(ApprovalStatus.PENDING_APPROVAL, response.status)
        assertEquals(trail.id, response.trailId)
        assertTrue(response.decisions.isEmpty())
    }

    @Test
    fun `approve transitions approval to APPROVED when no required approvers`() {
        val trail = createTrail()
        val approval = approvalService.requestApproval(CreateApprovalRequest(trailId = trail.id))
        val result = approvalService.approve(approval.id, ApproveRequest(approverIdentity = "alice"))
        assertEquals(ApprovalStatus.APPROVED, result.status)
        assertNotNull(result.resolvedAt)
        assertEquals(1, result.decisions.size)
    }

    @Test
    fun `approve keeps PENDING_APPROVAL when not all required approvers have approved`() {
        val trail = createTrail()
        val approval = approvalService.requestApproval(CreateApprovalRequest(trailId = trail.id, requiredApprovers = listOf("alice", "bob")))
        val result = approvalService.approve(approval.id, ApproveRequest(approverIdentity = "alice"))
        assertEquals(ApprovalStatus.PENDING_APPROVAL, result.status)
    }

    @Test
    fun `approve transitions to APPROVED when all required approvers have approved`() {
        val trail = createTrail()
        val approval = approvalService.requestApproval(CreateApprovalRequest(trailId = trail.id, requiredApprovers = listOf("alice", "bob")))
        approvalService.approve(approval.id, ApproveRequest(approverIdentity = "alice"))
        val result = approvalService.approve(approval.id, ApproveRequest(approverIdentity = "bob"))
        assertEquals(ApprovalStatus.APPROVED, result.status)
    }

    @Test
    fun `reject transitions approval to REJECTED`() {
        val trail = createTrail()
        val approval = approvalService.requestApproval(CreateApprovalRequest(trailId = trail.id))
        val result = approvalService.reject(approval.id, RejectRequest(approverIdentity = "alice", comments = "Not ready"))
        assertEquals(ApprovalStatus.REJECTED, result.status)
        assertNotNull(result.resolvedAt)
    }

    @Test
    fun `approve throws ConflictException when approval is already APPROVED`() {
        val trail = createTrail()
        val approval = approvalService.requestApproval(CreateApprovalRequest(trailId = trail.id))
        approvalService.approve(approval.id, ApproveRequest(approverIdentity = "alice"))
        assertThrows<ConflictException> {
            approvalService.approve(approval.id, ApproveRequest(approverIdentity = "bob"))
        }
    }

    @Test
    fun `getApproval throws NotFoundException for unknown id`() {
        assertThrows<NotFoundException> {
            approvalService.getApproval(UUID.randomUUID())
        }
    }
}
