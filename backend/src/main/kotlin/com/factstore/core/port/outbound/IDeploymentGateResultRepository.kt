package com.factstore.core.port.outbound

import com.factstore.core.domain.DeploymentGateResult
import java.util.UUID

interface IDeploymentGateResultRepository {
    fun save(result: DeploymentGateResult): DeploymentGateResult
    fun findAll(): List<DeploymentGateResult>
    fun findByArtifactSha256(sha: String): List<DeploymentGateResult>
}
