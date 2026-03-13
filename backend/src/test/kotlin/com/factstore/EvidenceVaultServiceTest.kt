package com.factstore

import com.factstore.application.AttestationService
import com.factstore.application.EvidenceVaultService
import com.factstore.application.FlowService
import com.factstore.application.TrailService
import com.factstore.application.toResponse
import com.factstore.core.domain.AttestationStatus
import com.factstore.dto.CreateAttestationRequest
import com.factstore.dto.CreateFlowRequest
import com.factstore.dto.CreateTrailRequest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@Transactional
class EvidenceVaultServiceTest {

    @Autowired lateinit var evidenceVaultService: EvidenceVaultService
    @Autowired lateinit var flowService: FlowService
    @Autowired lateinit var trailService: TrailService
    @Autowired lateinit var attestationService: AttestationService

    private fun createAttestation(): UUID {
        val flow = flowService.createFlow(CreateFlowRequest("vault-flow-${System.nanoTime()}", "desc"))
        val trail = trailService.createTrail(CreateTrailRequest(
            flowId = flow.id,
            gitCommitSha = "abc123",
            gitBranch = "main",
            gitAuthor = "dev",
            gitAuthorEmail = "dev@example.com"
        ))
        return attestationService.recordAttestation(
            trail.id,
            CreateAttestationRequest("junit", AttestationStatus.PASSED)
        ).id
    }

    // ----- storeExternal tests -----

    @Test
    fun `storeExternal creates record with externalUrl and no inline content`() {
        val attestationId = createAttestation()
        val ref = evidenceVaultService.storeExternal(
            attestationId = attestationId,
            fileName = "results.xml",
            contentType = "application/xml",
            externalUrl = "https://my-bucket.s3.amazonaws.com/results.xml?X-Amz-Signature=secret123",
            sha256Hash = "a".repeat(64),
            fileSizeBytes = 1024L
        )
        assertNotNull(ref.id)
        assertEquals("results.xml", ref.fileName)
        assertEquals("a".repeat(64), ref.sha256Hash)
        assertEquals(1024L, ref.fileSizeBytes)
        assertNull(ref.content, "Inline content must be null for external references")
        assertEquals(
            "https://my-bucket.s3.amazonaws.com/results.xml?X-Amz-Signature=secret123",
            ref.externalUrl
        )
    }

    @Test
    fun `storeExternal response includes externalUrl field`() {
        val attestationId = createAttestation()
        val ref = evidenceVaultService.storeExternal(
            attestationId = attestationId,
            fileName = "report.html",
            contentType = "text/html",
            externalUrl = "https://ci.example.com/artifacts/report.html",
            sha256Hash = "b".repeat(64),
            fileSizeBytes = 2048L
        )
        val resp = ref.toResponse()
        assertEquals("https://ci.example.com/artifacts/report.html", resp.externalUrl)
        assertEquals("report.html", resp.fileName)
    }

    // ----- verifyIntegrity tests -----

    @Test
    fun `verifyIntegrity returns true for valid inline content`() {
        val attestationId = createAttestation()
        val content = "trusted evidence".toByteArray()
        val stored = evidenceVaultService.store(attestationId, "ev.txt", "text/plain", content)
        assertTrue(evidenceVaultService.verifyIntegrity(stored.id))
    }

    @Test
    fun `verifyIntegrity returns false for unknown id`() {
        assertFalse(evidenceVaultService.verifyIntegrity(UUID.randomUUID()))
    }

    @Test
    fun `verifyIntegrity returns true for external reference skipping server-side validation`() {
        val attestationId = createAttestation()
        val ref = evidenceVaultService.storeExternal(
            attestationId = attestationId,
            fileName = "ext.zip",
            contentType = "application/zip",
            externalUrl = "https://example.com/ext.zip",
            sha256Hash = "d".repeat(64),
            fileSizeBytes = 9999L
        )
        // Must return true (and not throw), even though content cannot be re-hashed server-side
        assertTrue(evidenceVaultService.verifyIntegrity(ref.id))
    }
}

