package com.factstore

import com.factstore.core.domain.AttestationStatus
import com.factstore.core.domain.TrailStatus
import com.factstore.dto.*
import com.factstore.core.port.outbound.ITrailRepository
import com.factstore.application.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@Transactional
class AttestationServiceTest {

    @Autowired lateinit var flowService: FlowService
    @Autowired lateinit var trailService: TrailService
    @Autowired lateinit var attestationService: AttestationService
    @Autowired lateinit var trailRepository: ITrailRepository

    private fun setupTrail(): UUID {
        val flow = flowService.createFlow(CreateFlowRequest("flow-att-${System.nanoTime()}", "desc", listOf("junit")))
        val trail = trailService.createTrail(CreateTrailRequest(
            flowId = flow.id,
            gitCommitSha = "abc",
            gitBranch = "main",
            gitAuthor = "author",
            gitAuthorEmail = "a@b.com"
        ))
        return trail.id
    }

    @Test
    fun `record PASSED attestation succeeds`() {
        val trailId = setupTrail()
        val resp = attestationService.recordAttestation(trailId, CreateAttestationRequest("junit", AttestationStatus.PASSED))
        assertEquals("junit", resp.type)
        assertEquals(AttestationStatus.PASSED, resp.status)
        assertEquals(trailId, resp.trailId)
    }

    @Test
    fun `record FAILED attestation marks trail NON_COMPLIANT`() {
        val trailId = setupTrail()
        attestationService.recordAttestation(trailId, CreateAttestationRequest("snyk", AttestationStatus.FAILED))
        val trail = trailRepository.findById(trailId)!!
        assertEquals(TrailStatus.NON_COMPLIANT, trail.status)
    }

    @Test
    fun `list attestations for trail`() {
        val trailId = setupTrail()
        attestationService.recordAttestation(trailId, CreateAttestationRequest("junit", AttestationStatus.PASSED))
        attestationService.recordAttestation(trailId, CreateAttestationRequest("snyk", AttestationStatus.PASSED))
        val list = attestationService.listAttestations(trailId)
        assertEquals(2, list.size)
    }

    @Test
    fun `upload evidence updates attestation hash`() {
        val trailId = setupTrail()
        val att = attestationService.recordAttestation(trailId, CreateAttestationRequest("junit", AttestationStatus.PASSED))
        val content = "test evidence content".toByteArray()
        val ev = attestationService.uploadEvidence(trailId, att.id, "report.txt", "text/plain", content)
        assertNotNull(ev.sha256Hash)
        assertTrue(ev.sha256Hash.length == 64) // SHA256 hex
        assertEquals("report.txt", ev.fileName)
        assertEquals(content.size.toLong(), ev.fileSizeBytes)
    }

    @Test
    fun `record attestation for unknown trail throws NotFoundException`() {
        org.junit.jupiter.api.assertThrows<com.factstore.exception.NotFoundException> {
            attestationService.recordAttestation(UUID.randomUUID(), CreateAttestationRequest("junit", AttestationStatus.PASSED))
        }
    }

    @Test
    fun `should store and retrieve attestation data JSON`() {
        val trailId = setupTrail()
        val json = """{"tests": 42, "passed": 42}"""
        val resp = attestationService.recordAttestation(
            trailId,
            CreateAttestationRequest("junit", AttestationStatus.PASSED, attestationData = json)
        )
        assertEquals(json, resp.attestationData)

        val list = attestationService.listAttestations(trailId)
        assertEquals(json, list.first().attestationData)

        val respNoData = attestationService.recordAttestation(
            trailId,
            CreateAttestationRequest("snyk", AttestationStatus.PASSED)
        )
        assertNull(respNoData.attestationData)
    }

    @Test
    fun `should store and retrieve multiple external URLs on attestation`() {
        val trailId = setupTrail()
        val urls = listOf("https://jira.example.com/TICKET-1", "https://github.com/pr/42")
        val resp = attestationService.recordAttestation(
            trailId,
            CreateAttestationRequest("junit", AttestationStatus.PASSED, externalUrls = urls)
        )
        assertEquals(urls, resp.externalUrls)

        val list = attestationService.listAttestations(trailId)
        assertEquals(urls, list.first().externalUrls)
    }

    @Test
    fun `should store and retrieve key-value annotations on attestation`() {
        val trailId = setupTrail()
        val annotations = mapOf("env" to "production", "team" to "backend")
        val resp = attestationService.recordAttestation(
            trailId,
            CreateAttestationRequest("junit", AttestationStatus.PASSED, annotations = annotations)
        )
        assertEquals(annotations, resp.annotations)

        val list = attestationService.listAttestations(trailId)
        assertEquals(annotations, list.first().annotations)
    }

    @Test
    fun `should store and retrieve git commit info on attestation`() {
        val trailId = setupTrail()
        val sha = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2"
        val branch = "feature/my-branch"
        val repoUrl = "https://github.com/example/repo"
        val resp = attestationService.recordAttestation(
            trailId,
            CreateAttestationRequest(
                "junit", AttestationStatus.PASSED,
                gitCommitSha = sha, gitBranch = branch, gitRepoUrl = repoUrl
            )
        )
        assertEquals(sha, resp.gitCommitSha)
        assertEquals(branch, resp.gitBranch)
        assertEquals(repoUrl, resp.gitRepoUrl)

        val list = attestationService.listAttestations(trailId)
        val first = list.first()
        assertEquals(sha, first.gitCommitSha)
        assertEquals(branch, first.gitBranch)
        assertEquals(repoUrl, first.gitRepoUrl)
    }
}
