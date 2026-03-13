package com.factstore.adapter.outbound

import com.factstore.config.VaultProperties
import com.factstore.core.port.outbound.ISecureEvidenceStore
import com.factstore.core.port.outbound.VaultStorageReceipt
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.vault.core.VaultTemplate
import org.springframework.vault.support.VaultResponse

/**
 * HashiCorp Vault KV v2 adapter for secure evidence storage.
 *
 * Stores compliance evidence at structured paths within the Vault KV backend:
 *   `{kv.backend}/data/evidence/{entityType}/{entityId}/{evidenceType}`
 *
 * This adapter is activated only when `vault.enabled=true` in application configuration.
 * For local development, start the Vault dev server via Docker Compose.
 *
 * Authentication methods supported: TOKEN, APPROLE (KUBERNETES planned).
 */
@Component
@ConditionalOnProperty(name = ["vault.enabled"], havingValue = "true")
class VaultKvAdapter(
    private val vaultTemplate: VaultTemplate,
    private val vaultProperties: VaultProperties
) : ISecureEvidenceStore {

    private val log = LoggerFactory.getLogger(VaultKvAdapter::class.java)

    private val kvBackend: String get() = vaultProperties.kv.backend

    override fun storeEvidence(
        entityType: String,
        entityId: String,
        evidenceType: String,
        data: Map<String, String>
    ): VaultStorageReceipt {
        val path = buildPath(entityType, entityId, evidenceType)
        log.info("Storing evidence at Vault path: {}", path)
        val kvDataPath = kvDataPath(entityType, entityId, evidenceType)
        val response: VaultResponse? = vaultTemplate.write(kvDataPath, mapOf("data" to data))
        val version = extractVersion(response)
        return VaultStorageReceipt(path = path, version = version)
    }

    override fun retrieveEvidence(
        entityType: String,
        entityId: String,
        evidenceType: String
    ): Map<String, String>? {
        val path = kvDataPath(entityType, entityId, evidenceType)
        log.debug("Retrieving evidence from Vault path: {}", path)
        return try {
            val response = vaultTemplate.read(path) ?: return null
            @Suppress("UNCHECKED_CAST")
            (response.data?.get("data") as? Map<String, String>)
        } catch (e: Exception) {
            log.warn("Failed to retrieve evidence from Vault path {}: {}", path, e.message)
            null
        }
    }

    override fun listEvidence(entityType: String, entityId: String): List<String> {
        val listPath = "${kvBackend}/metadata/evidence/$entityType/$entityId"
        log.debug("Listing evidence keys at Vault path: {}", listPath)
        return try {
            val response = vaultTemplate.list(listPath) ?: emptyList()
            response
        } catch (e: Exception) {
            log.warn("Failed to list evidence at Vault path {}: {}", listPath, e.message)
            emptyList()
        }
    }

    override fun deleteEvidence(entityType: String, entityId: String, evidenceType: String) {
        val path = kvDataPath(entityType, entityId, evidenceType)
        log.info("Soft-deleting evidence at Vault path: {}", path)
        vaultTemplate.delete(path)
    }

    override fun isHealthy(): Boolean {
        return try {
            vaultTemplate.opsForSys().health().isInitialized
        } catch (e: Exception) {
            log.warn("Vault health check failed: {}", e.message)
            false
        }
    }

    /**
     * Logical KV v2 data path for reading/writing:
     *   `{backend}/data/evidence/{entityType}/{entityId}/{evidenceType}`
     */
    private fun kvDataPath(entityType: String, entityId: String, evidenceType: String): String =
        "$kvBackend/data/evidence/$entityType/$entityId/$evidenceType"

    /**
     * Human-readable canonical path (without the internal `/data/` prefix).
     */
    private fun buildPath(entityType: String, entityId: String, evidenceType: String): String =
        "$kvBackend/evidence/$entityType/$entityId/$evidenceType"

    private fun extractVersion(response: VaultResponse?): Int {
        @Suppress("UNCHECKED_CAST")
        val metadata = response?.data?.get("metadata") as? Map<String, Any>
        return (metadata?.get("version") as? Number)?.toInt() ?: 0
    }
}
