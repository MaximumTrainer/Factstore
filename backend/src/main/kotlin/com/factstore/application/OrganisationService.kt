package com.factstore.application

import com.factstore.core.domain.Organisation
import com.factstore.core.port.inbound.IOrganisationService
import com.factstore.core.port.outbound.IOrganisationRepository
import com.factstore.dto.CreateOrganisationRequest
import com.factstore.dto.OrganisationResponse
import com.factstore.dto.UpdateOrganisationRequest
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class OrganisationService(private val organisationRepository: IOrganisationRepository) : IOrganisationService {

    private val log = LoggerFactory.getLogger(OrganisationService::class.java)

    override fun createOrganisation(request: CreateOrganisationRequest): OrganisationResponse {
        if (organisationRepository.existsBySlug(request.slug)) {
            throw ConflictException("Organisation with slug '${request.slug}' already exists")
        }
        val organisation = Organisation(
            slug = request.slug,
            name = request.name,
            description = request.description
        )
        val saved = organisationRepository.save(organisation)
        log.info("Created organisation: ${saved.id} - ${saved.slug}")
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    override fun listOrganisations(): List<OrganisationResponse> =
        organisationRepository.findAll().map { it.toResponse() }

    @Transactional(readOnly = true)
    override fun getOrganisation(id: UUID): OrganisationResponse =
        (organisationRepository.findById(id) ?: throw NotFoundException("Organisation not found: $id")).toResponse()

    override fun updateOrganisation(id: UUID, request: UpdateOrganisationRequest): OrganisationResponse {
        val org = organisationRepository.findById(id)
            ?: throw NotFoundException("Organisation not found: $id")
        request.name?.let { org.name = it }
        request.description?.let { org.description = it }
        org.updatedAt = Instant.now()
        return organisationRepository.save(org).toResponse()
    }

    override fun deleteOrganisation(id: UUID) {
        if (!organisationRepository.existsById(id)) throw NotFoundException("Organisation not found: $id")
        organisationRepository.deleteById(id)
        log.info("Deleted organisation: $id")
    }
}

fun Organisation.toResponse() = OrganisationResponse(
    id = id,
    slug = slug,
    name = name,
    description = description,
    createdAt = createdAt,
    updatedAt = updatedAt
)
