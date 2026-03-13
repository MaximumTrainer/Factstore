package com.factstore.application

import com.factstore.core.domain.OwnerType
import com.factstore.core.domain.ServiceAccount
import com.factstore.core.port.inbound.IServiceAccountService
import com.factstore.core.port.outbound.IApiKeyRepository
import com.factstore.core.port.outbound.IServiceAccountRepository
import com.factstore.dto.ApiKeyCreatedResponse
import com.factstore.dto.ApiKeyResponse
import com.factstore.dto.CreateApiKeyRequest
import com.factstore.dto.CreateServiceAccountRequest
import com.factstore.dto.ServiceAccountResponse
import com.factstore.dto.UpdateServiceAccountRequest
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class ServiceAccountService(
    private val serviceAccountRepository: IServiceAccountRepository,
    private val apiKeyRepository: IApiKeyRepository,
    private val apiKeyService: ApiKeyService
) : IServiceAccountService {

    private val log = LoggerFactory.getLogger(ServiceAccountService::class.java)

    override fun createServiceAccount(request: CreateServiceAccountRequest): ServiceAccountResponse {
        if (serviceAccountRepository.existsByName(request.name)) {
            throw ConflictException("Service account with name '${request.name}' already exists")
        }
        val account = ServiceAccount(name = request.name, description = request.description)
        val saved = serviceAccountRepository.save(account)
        log.info("Created service account: ${saved.id} - ${saved.name}")
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    override fun listServiceAccounts(): List<ServiceAccountResponse> =
        serviceAccountRepository.findAll().map { it.toResponse() }

    @Transactional(readOnly = true)
    override fun getServiceAccount(id: UUID): ServiceAccountResponse =
        (serviceAccountRepository.findById(id)
            ?: throw NotFoundException("Service account not found: $id")).toResponse()

    override fun updateServiceAccount(id: UUID, request: UpdateServiceAccountRequest): ServiceAccountResponse {
        val account = serviceAccountRepository.findById(id)
            ?: throw NotFoundException("Service account not found: $id")
        request.name?.let { newName ->
            if (newName != account.name && serviceAccountRepository.existsByName(newName)) {
                throw ConflictException("Service account with name '$newName' already exists")
            }
            account.name = newName
        }
        request.description?.let { account.description = it }
        account.updatedAt = Instant.now()
        return serviceAccountRepository.save(account).toResponse()
    }

    override fun deleteServiceAccount(id: UUID) {
        if (!serviceAccountRepository.existsById(id)) {
            throw NotFoundException("Service account not found: $id")
        }
        // Cascade-delete all API keys for this service account
        apiKeyRepository.deleteByOwnerId(id)
        serviceAccountRepository.deleteById(id)
        log.info("Deleted service account: $id (and all its API keys)")
    }

    override fun createApiKey(serviceAccountId: UUID, label: String, ttlDays: Int?): ApiKeyCreatedResponse {
        if (!serviceAccountRepository.existsById(serviceAccountId)) {
            throw NotFoundException("Service account not found: $serviceAccountId")
        }
        return apiKeyService.createApiKey(
            CreateApiKeyRequest(
                ownerId = serviceAccountId,
                label = label,
                ownerType = OwnerType.SERVICE_ACCOUNT,
                ttlDays = ttlDays
            )
        )
    }

    @Transactional(readOnly = true)
    override fun listApiKeys(serviceAccountId: UUID): List<ApiKeyResponse> {
        if (!serviceAccountRepository.existsById(serviceAccountId)) {
            throw NotFoundException("Service account not found: $serviceAccountId")
        }
        return apiKeyRepository.findByOwnerId(serviceAccountId).map { it.toResponse() }
    }

    override fun revokeApiKey(serviceAccountId: UUID, keyId: UUID) {
        if (!serviceAccountRepository.existsById(serviceAccountId)) {
            throw NotFoundException("Service account not found: $serviceAccountId")
        }
        val key = apiKeyRepository.findById(keyId)
            ?: throw NotFoundException("API key not found: $keyId")
        if (key.ownerId != serviceAccountId) {
            throw NotFoundException("API key $keyId does not belong to service account $serviceAccountId")
        }
        apiKeyService.revokeApiKey(keyId)
    }
}

fun ServiceAccount.toResponse() = ServiceAccountResponse(
    id = id,
    name = name,
    description = description,
    createdAt = createdAt,
    updatedAt = updatedAt
)
