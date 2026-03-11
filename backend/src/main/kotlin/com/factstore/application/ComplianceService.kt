package com.factstore.application

import com.factstore.core.domain.AttestationStatus
import com.factstore.core.port.inbound.IComplianceService
import com.factstore.core.port.outbound.IArtifactRepository
import com.factstore.core.port.outbound.IAttestationRepository
import com.factstore.core.port.outbound.IEvidenceFileRepository
import com.factstore.core.port.outbound.IFlowRepository
import com.factstore.core.port.outbound.ITrailRepository
import com.factstore.dto.ChainOfCustodyResponse
import com.factstore.dto.ComplianceStatus
import com.factstore.exception.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ComplianceService(
    private val artifactRepository: IArtifactRepository,
    private val trailRepository: ITrailRepository,
    private val flowRepository: IFlowRepository,
    private val attestationRepository: IAttestationRepository,
    private val evidenceFileRepository: IEvidenceFileRepository
) : IComplianceService {

    override fun getChainOfCustody(sha256Digest: String): ChainOfCustodyResponse {
        val artifacts = artifactRepository.findBySha256Digest(sha256Digest)
        if (artifacts.isEmpty()) throw NotFoundException("No artifact found with digest: $sha256Digest")

        val artifact = artifacts.first()
        val trail = trailRepository.findById(artifact.trailId)
            ?: throw NotFoundException("Trail not found: ${artifact.trailId}")
        val flow = flowRepository.findById(trail.flowId)
            ?: throw NotFoundException("Flow not found: ${trail.flowId}")
        val attestations = attestationRepository.findByTrailId(trail.id)
        val evidenceFiles = attestations.flatMap { evidenceFileRepository.findByAttestationId(it.id) }

        val required = flow.requiredAttestationTypes
        val passedTypes = attestations.filter { it.status == AttestationStatus.PASSED }.map { it.type }.toSet()
        val complianceStatus = if (required.isEmpty() || required.all { it in passedTypes })
            ComplianceStatus.COMPLIANT else ComplianceStatus.NON_COMPLIANT

        return ChainOfCustodyResponse(
            artifact = artifact.toResponse(),
            trail = trail.toResponse(),
            flow = flow.toResponse(),
            attestations = attestations.map { it.toResponse() },
            evidenceFiles = evidenceFiles.map { it.toResponse() },
            complianceStatus = complianceStatus
        )
    }
}
