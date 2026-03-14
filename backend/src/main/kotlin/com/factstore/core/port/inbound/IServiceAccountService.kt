package com.factstore.core.port.inbound

import com.factstore.dto.ApiKeyCreatedResponse
import com.factstore.dto.ApiKeyResponse
import com.factstore.dto.CreateServiceAccountRequest
import com.factstore.dto.ServiceAccountResponse
import com.factstore.dto.UpdateServiceAccountRequest
import java.util.UUID

interface IServiceAccountService {
    fun createServiceAccount(request: CreateServiceAccountRequest): ServiceAccountResponse
    fun listServiceAccounts(): List<ServiceAccountResponse>
    fun getServiceAccount(id: UUID): ServiceAccountResponse
    fun updateServiceAccount(id: UUID, request: UpdateServiceAccountRequest): ServiceAccountResponse
    fun deleteServiceAccount(id: UUID)
    /** Generates an API key for the given service account and returns the plain-text key once. */
    fun createApiKey(serviceAccountId: UUID, label: String, ttlDays: Int?): ApiKeyCreatedResponse
    fun listApiKeys(serviceAccountId: UUID): List<ApiKeyResponse>
    fun revokeApiKey(serviceAccountId: UUID, keyId: UUID)
}
