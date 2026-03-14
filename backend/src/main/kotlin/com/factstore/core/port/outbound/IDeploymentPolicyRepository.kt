package com.factstore.core.port.outbound

import com.factstore.core.domain.DeploymentPolicy
import java.util.UUID

interface IDeploymentPolicyRepository {
    fun save(policy: DeploymentPolicy): DeploymentPolicy
    fun findById(id: UUID): DeploymentPolicy?
    fun findAll(): List<DeploymentPolicy>
    fun findByFlowId(flowId: UUID): List<DeploymentPolicy>
    fun findByEnvironmentId(environmentId: UUID): List<DeploymentPolicy>
    fun findActive(): List<DeploymentPolicy>
    fun existsById(id: UUID): Boolean
    fun deleteById(id: UUID)
}
