package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.DeploymentPolicy
import com.factstore.core.port.outbound.IDeploymentPolicyRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface DeploymentPolicyRepositoryJpa : JpaRepository<DeploymentPolicy, UUID> {
    fun findByFlowId(flowId: UUID): List<DeploymentPolicy>
    fun findByEnvironmentId(environmentId: UUID): List<DeploymentPolicy>
    fun findByIsActiveTrue(): List<DeploymentPolicy>
}

@Component
class DeploymentPolicyRepositoryAdapter(private val jpa: DeploymentPolicyRepositoryJpa) : IDeploymentPolicyRepository {
    override fun save(policy: DeploymentPolicy): DeploymentPolicy = jpa.save(policy)
    override fun findById(id: UUID): DeploymentPolicy? = jpa.findById(id).orElse(null)
    override fun findAll(): List<DeploymentPolicy> = jpa.findAll()
    override fun findByFlowId(flowId: UUID): List<DeploymentPolicy> = jpa.findByFlowId(flowId)
    override fun findByEnvironmentId(environmentId: UUID): List<DeploymentPolicy> = jpa.findByEnvironmentId(environmentId)
    override fun findActive(): List<DeploymentPolicy> = jpa.findByIsActiveTrue()
    override fun existsById(id: UUID): Boolean = jpa.existsById(id)
    override fun deleteById(id: UUID) = jpa.deleteById(id)
}
