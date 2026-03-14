package com.factstore.application

import com.factstore.core.domain.PolicyAttachment
import com.factstore.core.port.inbound.IPolicyAttachmentService
import com.factstore.core.port.outbound.IEnvironmentRepository
import com.factstore.core.port.outbound.IPolicyAttachmentRepository
import com.factstore.core.port.outbound.IPolicyRepository
import com.factstore.dto.CreatePolicyAttachmentRequest
import com.factstore.dto.PolicyAttachmentResponse
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional
class PolicyAttachmentService(
    private val attachmentRepository: IPolicyAttachmentRepository,
    private val policyRepository: IPolicyRepository,
    private val environmentRepository: IEnvironmentRepository
) : IPolicyAttachmentService {

    private val log = LoggerFactory.getLogger(PolicyAttachmentService::class.java)

    override fun createAttachment(request: CreatePolicyAttachmentRequest): PolicyAttachmentResponse {
        if (!policyRepository.existsById(request.policyId)) {
            throw NotFoundException("Policy not found: ${request.policyId}")
        }
        if (!environmentRepository.existsById(request.environmentId)) {
            throw NotFoundException("Environment not found: ${request.environmentId}")
        }
        if (attachmentRepository.existsByPolicyIdAndEnvironmentId(request.policyId, request.environmentId)) {
            throw ConflictException("Policy ${request.policyId} is already attached to environment ${request.environmentId}")
        }
        val attachment = PolicyAttachment(
            policyId = request.policyId,
            environmentId = request.environmentId
        )
        val saved = attachmentRepository.save(attachment)
        log.info("Created policy attachment: ${saved.id}")
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    override fun listAttachments(): List<PolicyAttachmentResponse> =
        attachmentRepository.findAll().map { it.toResponse() }

    @Transactional(readOnly = true)
    override fun getAttachment(id: UUID): PolicyAttachmentResponse =
        (attachmentRepository.findById(id) ?: throw NotFoundException("PolicyAttachment not found: $id")).toResponse()

    override fun deleteAttachment(id: UUID) {
        if (!attachmentRepository.existsById(id)) throw NotFoundException("PolicyAttachment not found: $id")
        attachmentRepository.deleteById(id)
        log.info("Deleted policy attachment: $id")
    }
}

fun PolicyAttachment.toResponse() = PolicyAttachmentResponse(
    id = id,
    policyId = policyId,
    environmentId = environmentId,
    createdAt = createdAt
)
