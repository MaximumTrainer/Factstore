package com.factstore.application

import com.factstore.core.domain.Policy
import com.factstore.core.domain.PolicyVersion
import com.factstore.core.port.inbound.IPolicyService
import com.factstore.core.port.outbound.IPolicyRepository
import com.factstore.core.port.outbound.IPolicyVersionRepository
import com.factstore.dto.CreatePolicyRequest
import com.factstore.dto.PolicyResponse
import com.factstore.dto.PolicyVersionResponse
import com.factstore.dto.UpdatePolicyRequest
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class PolicyService(
    private val policyRepository: IPolicyRepository,
    private val policyVersionRepository: IPolicyVersionRepository
) : IPolicyService {

    private val log = LoggerFactory.getLogger(PolicyService::class.java)

    override fun createPolicy(request: CreatePolicyRequest): PolicyResponse {
        if (policyRepository.existsByName(request.name)) {
            throw ConflictException("Policy with name '${request.name}' already exists")
        }
        request.policyYaml?.let { PolicyYamlValidator.validate(it) }
        val policy = Policy(
            name = request.name,
            enforceProvenance = request.enforceProvenance,
            enforceTrailCompliance = request.enforceTrailCompliance,
            orgSlug = request.orgSlug
        ).also {
            it.requiredAttestationTypes = request.requiredAttestationTypes
            it.policyYaml = request.policyYaml
        }
        val saved = policyRepository.save(policy)
        log.info("Created policy: ${saved.id} - ${saved.name}")
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    override fun listPolicies(): List<PolicyResponse> =
        policyRepository.findAll().map { it.toResponse() }

    @Transactional(readOnly = true)
    override fun getPolicy(id: UUID): PolicyResponse =
        (policyRepository.findById(id) ?: throw NotFoundException("Policy not found: $id")).toResponse()

    override fun updatePolicy(id: UUID, request: UpdatePolicyRequest): PolicyResponse {
        val policy = policyRepository.findById(id) ?: throw NotFoundException("Policy not found: $id")
        request.policyYaml?.let { PolicyYamlValidator.validate(it) }

        // Snapshot current state before updating
        val snapshot = policy.snapshot()
        policyVersionRepository.save(
            PolicyVersion(
                policyId = policy.id,
                version = policy.version,
                content = snapshot,
                changeComment = request.changeComment
            )
        )
        policy.version++

        request.name?.let {
            if (it != policy.name && policyRepository.existsByName(it)) {
                throw ConflictException("Policy with name '$it' already exists")
            }
            policy.name = it
        }
        request.enforceProvenance?.let { policy.enforceProvenance = it }
        request.enforceTrailCompliance?.let { policy.enforceTrailCompliance = it }
        request.requiredAttestationTypes?.let { policy.requiredAttestationTypes = it }
        request.policyYaml?.let { policy.policyYaml = it }
        policy.updatedAt = Instant.now()
        return policyRepository.save(policy).toResponse()
    }

    override fun deletePolicy(id: UUID) {
        if (!policyRepository.existsById(id)) throw NotFoundException("Policy not found: $id")
        policyRepository.deleteById(id)
        log.info("Deleted policy: $id")
    }

    override fun updateWasmModule(id: UUID, wasmContent: String) {
        val policy = policyRepository.findById(id) ?: throw NotFoundException("Policy not found: $id")
        policy.wasmModuleContent = wasmContent
        policyRepository.save(policy)
    }

    @Transactional(readOnly = true)
    override fun listPolicyVersions(policyId: UUID): List<PolicyVersionResponse> {
        if (!policyRepository.existsById(policyId)) throw NotFoundException("Policy not found: $policyId")
        return policyVersionRepository.findAllByPolicyId(policyId).map { it.toVersionResponse() }
    }
}

private fun Policy.snapshot(): String =
    "{\"name\":\"$name\",\"version\":$version,\"enforceProvenance\":$enforceProvenance," +
    "\"enforceTrailCompliance\":$enforceTrailCompliance,\"policyYaml\":${if (policyYaml != null) "\"${policyYaml!!.replace("\"", "\\\"")}\"" else "null"}}"

fun Policy.toResponse() = PolicyResponse(
    id = id,
    name = name,
    enforceProvenance = enforceProvenance,
    enforceTrailCompliance = enforceTrailCompliance,
    requiredAttestationTypes = requiredAttestationTypes,
    orgSlug = orgSlug,
    policyYaml = policyYaml,
    version = version,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun PolicyVersion.toVersionResponse() = PolicyVersionResponse(
    id = id,
    policyId = policyId,
    version = version,
    content = content,
    changeComment = changeComment,
    createdAt = createdAt
)

