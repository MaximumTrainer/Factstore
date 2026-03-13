package com.factstore.application

import com.factstore.core.domain.Environment
import com.factstore.core.port.inbound.IEnvironmentService
import com.factstore.core.port.outbound.IEnvironmentRepository
import com.factstore.dto.CreateEnvironmentRequest
import com.factstore.dto.EnvironmentResponse
import com.factstore.dto.UpdateEnvironmentRequest
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class EnvironmentService(private val environmentRepository: IEnvironmentRepository) : IEnvironmentService {

    private val log = LoggerFactory.getLogger(EnvironmentService::class.java)

    override fun createEnvironment(request: CreateEnvironmentRequest): EnvironmentResponse {
        if (environmentRepository.existsByName(request.name)) {
            throw ConflictException("Environment with name '${request.name}' already exists")
        }
        val environment = Environment(
            name = request.name,
            type = request.type,
            description = request.description
        )
        val saved = environmentRepository.save(environment)
        log.info("Created environment: ${saved.id} - ${saved.name}")
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    override fun listEnvironments(): List<EnvironmentResponse> =
        environmentRepository.findAll().map { it.toResponse() }

    @Transactional(readOnly = true)
    override fun getEnvironment(id: UUID): EnvironmentResponse =
        (environmentRepository.findById(id) ?: throw NotFoundException("Environment not found: $id")).toResponse()

    override fun updateEnvironment(id: UUID, request: UpdateEnvironmentRequest): EnvironmentResponse {
        val environment = environmentRepository.findById(id)
            ?: throw NotFoundException("Environment not found: $id")
        request.name?.let {
            if (it != environment.name && environmentRepository.existsByName(it)) {
                throw ConflictException("Environment with name '$it' already exists")
            }
            environment.name = it
        }
        request.type?.let { environment.type = it }
        request.description?.let { environment.description = it }
        environment.updatedAt = Instant.now()
        return environmentRepository.save(environment).toResponse()
    }

    override fun deleteEnvironment(id: UUID) {
        if (!environmentRepository.existsById(id)) throw NotFoundException("Environment not found: $id")
        environmentRepository.deleteById(id)
        log.info("Deleted environment: $id")
    }
}

fun Environment.toResponse() = EnvironmentResponse(
    id = id,
    name = name,
    type = type,
    description = description,
    createdAt = createdAt,
    updatedAt = updatedAt
)
