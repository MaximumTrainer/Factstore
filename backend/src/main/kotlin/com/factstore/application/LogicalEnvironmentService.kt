package com.factstore.application

import com.factstore.core.domain.LogicalEnvironment
import com.factstore.core.port.inbound.ILogicalEnvironmentService
import com.factstore.core.port.outbound.ILogicalEnvironmentRepository
import com.factstore.dto.CreateLogicalEnvironmentRequest
import com.factstore.dto.LogicalEnvironmentResponse
import com.factstore.dto.UpdateLogicalEnvironmentRequest
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class LogicalEnvironmentService(private val logicalEnvironmentRepository: ILogicalEnvironmentRepository) : ILogicalEnvironmentService {

    private val log = LoggerFactory.getLogger(LogicalEnvironmentService::class.java)

    override fun createLogicalEnvironment(request: CreateLogicalEnvironmentRequest): LogicalEnvironmentResponse {
        if (logicalEnvironmentRepository.existsByName(request.name)) {
            throw ConflictException("LogicalEnvironment with name '${request.name}' already exists")
        }
        val env = LogicalEnvironment(
            name = request.name,
            description = request.description
        )
        val saved = logicalEnvironmentRepository.save(env)
        log.info("Created logical environment: ${saved.id} - ${saved.name}")
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    override fun listLogicalEnvironments(): List<LogicalEnvironmentResponse> =
        logicalEnvironmentRepository.findAll().map { it.toResponse() }

    @Transactional(readOnly = true)
    override fun getLogicalEnvironment(id: UUID): LogicalEnvironmentResponse =
        (logicalEnvironmentRepository.findById(id) ?: throw NotFoundException("LogicalEnvironment not found: $id")).toResponse()

    override fun updateLogicalEnvironment(id: UUID, request: UpdateLogicalEnvironmentRequest): LogicalEnvironmentResponse {
        val env = logicalEnvironmentRepository.findById(id)
            ?: throw NotFoundException("LogicalEnvironment not found: $id")
        request.name?.let {
            if (it != env.name && logicalEnvironmentRepository.existsByName(it)) {
                throw ConflictException("LogicalEnvironment with name '$it' already exists")
            }
            env.name = it
        }
        request.description?.let { env.description = it }
        env.updatedAt = Instant.now()
        return logicalEnvironmentRepository.save(env).toResponse()
    }

    override fun deleteLogicalEnvironment(id: UUID) {
        if (!logicalEnvironmentRepository.existsById(id)) throw NotFoundException("LogicalEnvironment not found: $id")
        logicalEnvironmentRepository.deleteById(id)
        log.info("Deleted logical environment: $id")
    }
}

fun LogicalEnvironment.toResponse() = LogicalEnvironmentResponse(
    id = id,
    name = name,
    description = description,
    createdAt = createdAt,
    updatedAt = updatedAt
)
