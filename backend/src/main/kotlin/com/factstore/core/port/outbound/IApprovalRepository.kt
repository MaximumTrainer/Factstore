package com.factstore.core.port.outbound

import com.factstore.core.domain.Approval
import com.factstore.core.domain.ApprovalStatus
import java.util.UUID

interface IApprovalRepository {
    fun save(approval: Approval): Approval
    fun findById(id: UUID): Approval?
    fun findByTrailId(trailId: UUID): List<Approval>
    fun findByStatus(status: ApprovalStatus): List<Approval>
    fun findAll(): List<Approval>
    fun existsById(id: UUID): Boolean
}
