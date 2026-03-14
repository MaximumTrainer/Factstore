package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.Approval
import com.factstore.core.domain.ApprovalDecision
import com.factstore.core.domain.ApprovalStatus
import com.factstore.core.port.outbound.IApprovalDecisionRepository
import com.factstore.core.port.outbound.IApprovalRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ApprovalRepositoryJpa : JpaRepository<Approval, UUID> {
    fun findByTrailId(trailId: UUID): List<Approval>
    fun findByStatus(status: ApprovalStatus): List<Approval>
}

@Component
class ApprovalRepositoryAdapter(private val jpa: ApprovalRepositoryJpa) : IApprovalRepository {
    override fun save(approval: Approval): Approval = jpa.save(approval)
    override fun findById(id: UUID): Approval? = jpa.findById(id).orElse(null)
    override fun findByTrailId(trailId: UUID): List<Approval> = jpa.findByTrailId(trailId)
    override fun findByStatus(status: ApprovalStatus): List<Approval> = jpa.findByStatus(status)
    override fun findAll(): List<Approval> = jpa.findAll()
    override fun existsById(id: UUID): Boolean = jpa.existsById(id)
}

@Repository
interface ApprovalDecisionRepositoryJpa : JpaRepository<ApprovalDecision, UUID> {
    fun findByApprovalId(approvalId: UUID): List<ApprovalDecision>
}

@Component
class ApprovalDecisionRepositoryAdapter(private val jpa: ApprovalDecisionRepositoryJpa) : IApprovalDecisionRepository {
    override fun save(decision: ApprovalDecision): ApprovalDecision = jpa.save(decision)
    override fun findByApprovalId(approvalId: UUID): List<ApprovalDecision> = jpa.findByApprovalId(approvalId)
}
