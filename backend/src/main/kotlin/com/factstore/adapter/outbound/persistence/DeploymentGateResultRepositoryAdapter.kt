package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.DeploymentGateResult
import com.factstore.core.port.outbound.IDeploymentGateResultRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface DeploymentGateResultRepositoryJpa : JpaRepository<DeploymentGateResult, UUID> {
    fun findByArtifactSha256(artifactSha256: String): List<DeploymentGateResult>
}

@Component
class DeploymentGateResultRepositoryAdapter(private val jpa: DeploymentGateResultRepositoryJpa) : IDeploymentGateResultRepository {
    override fun save(result: DeploymentGateResult): DeploymentGateResult = jpa.save(result)
    override fun findAll(): List<DeploymentGateResult> = jpa.findAll()
    override fun findByArtifactSha256(sha: String): List<DeploymentGateResult> = jpa.findByArtifactSha256(sha)
}
