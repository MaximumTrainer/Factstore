package com.factstore.application

import com.factstore.config.VaultProperties
import com.factstore.core.port.inbound.IVaultEvidenceService
import com.factstore.core.port.outbound.ISecureEvidenceStore
import com.factstore.dto.StoreEvidenceRequest
import com.factstore.dto.VaultEvidenceListResponse
import com.factstore.dto.VaultEvidenceResponse
import com.factstore.dto.VaultHealthResponse
import com.factstore.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Application service providing secure evidence management backed by HashiCorp Vault.
 *
 * Evidence is stored in Vault's KV v2 secrets engine at structured paths:
 *   `secret/evidence/{entityType}/{entityId}/{evidenceType}`
 *
 * All evidence access operations are logged for audit purposes.
 * This service is only active when `vault.enabled=true`.
 */
@Service
@ConditionalOnProperty(name = ["vault.enabled"], havingValue = "true")
class VaultEvidenceService(
    private val secureEvidenceStore: ISecureEvidenceStore,
    private val vaultProperties: VaultProperties
) : IVaultEvidenceService {

    private val log = LoggerFactory.getLogger(VaultEvidenceService::class.java)

    override fun storeEvidence(
        entityType: String,
        entityId: String,
        request: StoreEvidenceRequest
    ): VaultEvidenceResponse {
        log.info(
            "Storing evidence: entityType={} entityId={} evidenceType={}",
            entityType, entityId, request.evidenceType
        )
        val receipt = secureEvidenceStore.storeEvidence(
            entityType = entityType,
            entityId = entityId,
            evidenceType = request.evidenceType,
            data = request.data
        )
        return VaultEvidenceResponse(
            entityType = entityType,
            entityId = entityId,
            evidenceType = request.evidenceType,
            vaultPath = receipt.path,
            version = receipt.version,
            storedAt = Instant.ofEpochMilli(receipt.storedAtEpochMs)
        )
    }

    override fun retrieveEvidence(
        entityType: String,
        entityId: String,
        evidenceType: String
    ): VaultEvidenceResponse {
        log.info(
            "Retrieving evidence metadata: entityType={} entityId={} evidenceType={}",
            entityType, entityId, evidenceType
        )
        // Verify the evidence exists; throw if not found
        secureEvidenceStore.retrieveEvidence(entityType, entityId, evidenceType)
            ?: throw NotFoundException(
                "No evidence found for entityType=$entityType entityId=$entityId evidenceType=$evidenceType"
            )
        val path = "${vaultProperties.kv.backend}/evidence/$entityType/$entityId/$evidenceType"
        // Note: version and storedAt are not available from a KV v2 read without a separate
        // metadata call. Version is returned as 0 and storedAt reflects retrieval time here.
        // A future enhancement could call the /metadata path to populate these fields.
        return VaultEvidenceResponse(
            entityType = entityType,
            entityId = entityId,
            evidenceType = evidenceType,
            vaultPath = path,
            version = 0,
            storedAt = Instant.now()
        )
    }

    override fun listEvidence(entityType: String, entityId: String): VaultEvidenceListResponse {
        log.info("Listing evidence: entityType={} entityId={}", entityType, entityId)
        val types = secureEvidenceStore.listEvidence(entityType, entityId)
        return VaultEvidenceListResponse(
            entityType = entityType,
            entityId = entityId,
            evidenceTypes = types
        )
    }

    override fun downloadEvidence(
        entityType: String,
        entityId: String,
        evidenceType: String
    ): Map<String, String> {
        log.info(
            "Downloading evidence: entityType={} entityId={} evidenceType={}",
            entityType, entityId, evidenceType
        )
        return secureEvidenceStore.retrieveEvidence(entityType, entityId, evidenceType)
            ?: throw NotFoundException(
                "No evidence found for entityType=$entityType entityId=$entityId evidenceType=$evidenceType"
            )
    }

    override fun deleteEvidence(entityType: String, entityId: String, evidenceType: String) {
        log.info(
            "Soft-deleting evidence: entityType={} entityId={} evidenceType={}",
            entityType, entityId, evidenceType
        )
        secureEvidenceStore.deleteEvidence(entityType, entityId, evidenceType)
    }

    override fun getHealth(): VaultHealthResponse {
        val healthy = secureEvidenceStore.isHealthy()
        val status = if (healthy) "Vault is reachable and initialised" else "Vault is unreachable or unhealthy"
        log.debug("Vault health check: healthy={}", healthy)
        return VaultHealthResponse(
            healthy = healthy,
            vaultUri = vaultProperties.uri,
            authMethod = vaultProperties.authentication.name,
            message = status
        )
    }
}
