package com.factstore.application

import com.factstore.core.domain.AttestationStatus
import com.factstore.core.port.inbound.IAssertService
import com.factstore.core.port.outbound.IArtifactRepository
import com.factstore.core.port.outbound.IAttestationRepository
import com.factstore.core.port.outbound.IFlowRepository
import com.factstore.dto.AssertRequest
import com.factstore.dto.AssertResponse
import com.factstore.dto.ComplianceStatus
import com.factstore.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class AssertService(
    private val artifactRepository: IArtifactRepository,
    private val attestationRepository: IAttestationRepository,
    private val flowRepository: IFlowRepository
) : IAssertService {

    private val log = LoggerFactory.getLogger(AssertService::class.java)

    override fun assertCompliance(request: AssertRequest): AssertResponse {
        val flow = flowRepository.findById(request.flowId)
            ?: throw NotFoundException("Flow not found: ${request.flowId}")

        val artifacts = artifactRepository.findBySha256Digest(request.sha256Digest)
        if (artifacts.isEmpty()) {
            return AssertResponse(
                sha256Digest = request.sha256Digest,
                flowId = request.flowId,
                status = ComplianceStatus.NON_COMPLIANT,
                missingAttestationTypes = flow.requiredAttestationTypes,
                failedAttestationTypes = emptyList(),
                details = "No artifacts found with digest ${request.sha256Digest}"
            )
        }

        val required = flow.requiredAttestationTypes
        if (required.isEmpty()) {
            return AssertResponse(
                sha256Digest = request.sha256Digest,
                flowId = request.flowId,
                status = ComplianceStatus.COMPLIANT,
                missingAttestationTypes = emptyList(),
                failedAttestationTypes = emptyList(),
                details = "Flow has no required attestation types; artifact is compliant"
            )
        }

        // For each artifact, check if its trail has all required attestations passed
        for (artifact in artifacts) {
            val attestations = attestationRepository.findByTrailId(artifact.trailId)
            val passedTypes = attestations
                .filter { it.status == AttestationStatus.PASSED }
                .map { it.type }
                .toSet()
            val failedTypes = attestations
                .filter { it.status == AttestationStatus.FAILED }
                .map { it.type }
                .toList()
            val missing = required.filter { it !in passedTypes }

            if (missing.isEmpty()) {
                log.info("Artifact ${request.sha256Digest} is COMPLIANT for flow ${request.flowId}")
                return AssertResponse(
                    sha256Digest = request.sha256Digest,
                    flowId = request.flowId,
                    status = ComplianceStatus.COMPLIANT,
                    missingAttestationTypes = emptyList(),
                    failedAttestationTypes = failedTypes,
                    details = "All required attestations passed"
                )
            }
        }

        // None of the trails are fully compliant - return the best result (least missing)
        val bestArtifact = artifacts.minByOrNull { artifact ->
            val attestations = attestationRepository.findByTrailId(artifact.trailId)
            val passedTypes = attestations.filter { it.status == AttestationStatus.PASSED }.map { it.type }.toSet()
            required.count { it !in passedTypes }
        }!!

        val attestations = attestationRepository.findByTrailId(bestArtifact.trailId)
        val passedTypes = attestations.filter { it.status == AttestationStatus.PASSED }.map { it.type }.toSet()
        val failedTypes = attestations.filter { it.status == AttestationStatus.FAILED }.map { it.type }
        val missing = required.filter { it !in passedTypes }

        log.info("Artifact ${request.sha256Digest} is NON_COMPLIANT for flow ${request.flowId}; missing: $missing")
        return AssertResponse(
            sha256Digest = request.sha256Digest,
            flowId = request.flowId,
            status = ComplianceStatus.NON_COMPLIANT,
            missingAttestationTypes = missing,
            failedAttestationTypes = failedTypes,
            details = "Missing required attestations: ${missing.joinToString(", ")}"
        )
    }
}
