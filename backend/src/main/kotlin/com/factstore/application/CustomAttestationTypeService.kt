package com.factstore.application

import com.factstore.core.domain.CustomAttestationType
import com.factstore.core.port.inbound.ICustomAttestationTypeService
import com.factstore.core.port.outbound.ICustomAttestationTypeRepository
import com.factstore.dto.CreateCustomAttestationTypeRequest
import com.factstore.dto.CustomAttestationTypeResponse
import com.factstore.dto.UpdateCustomAttestationTypeRequest
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class CustomAttestationTypeService(
    private val repository: ICustomAttestationTypeRepository
) : ICustomAttestationTypeService {

    private val log = LoggerFactory.getLogger(CustomAttestationTypeService::class.java)

    override fun createType(request: CreateCustomAttestationTypeRequest): CustomAttestationTypeResponse {
        if (repository.existsByName(request.name)) throw ConflictException("Custom attestation type '${request.name}' already exists")
        val type = CustomAttestationType(name = request.name, description = request.description, orgSlug = request.orgSlug)
        return repository.save(type).toResponse()
    }

    @Transactional(readOnly = true)
    override fun listTypes(includeArchived: Boolean): List<CustomAttestationTypeResponse> =
        if (includeArchived) repository.findAll().map { it.toResponse() }
        else repository.findAllActive().map { it.toResponse() }

    @Transactional(readOnly = true)
    override fun getType(id: UUID): CustomAttestationTypeResponse =
        repository.findById(id)?.toResponse() ?: throw NotFoundException("Custom attestation type not found: $id")

    override fun updateType(id: UUID, request: UpdateCustomAttestationTypeRequest): CustomAttestationTypeResponse {
        val type = repository.findById(id) ?: throw NotFoundException("Custom attestation type not found: $id")
        request.description?.let { type.description = it }
        request.orgSlug?.let { type.orgSlug = it }
        type.version++
        type.updatedAt = Instant.now()
        return repository.save(type).toResponse()
    }

    override fun archiveType(id: UUID): CustomAttestationTypeResponse {
        val type = repository.findById(id) ?: throw NotFoundException("Custom attestation type not found: $id")
        type.archivedAt = Instant.now()
        type.updatedAt = Instant.now()
        return repository.save(type).toResponse()
    }

    override fun unarchiveType(id: UUID): CustomAttestationTypeResponse {
        val type = repository.findById(id) ?: throw NotFoundException("Custom attestation type not found: $id")
        type.archivedAt = null
        type.updatedAt = Instant.now()
        return repository.save(type).toResponse()
    }
}

fun CustomAttestationType.toResponse() = CustomAttestationTypeResponse(
    id = id,
    name = name,
    description = description,
    version = version,
    orgSlug = orgSlug,
    archivedAt = archivedAt,
    createdAt = createdAt,
    updatedAt = updatedAt
)
