package com.factstore

import com.factstore.application.ArtifactService
import com.factstore.application.AttestationService
import com.factstore.application.FlowService
import com.factstore.core.domain.AttestationStatus
import com.factstore.core.port.inbound.IAssertService
import com.factstore.core.port.inbound.command.ITrailCommandHandler
import com.factstore.dto.command.CreateTrailCommand
import com.factstore.core.port.inbound.ITrailService
import com.factstore.dto.ComplianceStatus
import com.factstore.dto.CreateAttestationRequest
import com.factstore.dto.CreateFlowRequest
import com.factstore.dto.CreateTrailRequest
import com.factstore.dto.TrailResponse
import com.factstore.exception.NotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * #164: a secondary pipeline must be able to attach its attestations to the trail the
 * primary pipeline created, without hard-coding a UUID.
 */
@SpringBootTest
@Transactional
class TrailLookupTest {

    @Autowired lateinit var flowService: FlowService
    @Autowired lateinit var trailService: ITrailService
    @Autowired lateinit var attestationService: AttestationService
    @Autowired lateinit var artifactService: ArtifactService
    @Autowired lateinit var assertService: IAssertService
    @Autowired lateinit var trailCommandHandler: ITrailCommandHandler

    private fun newFlow(vararg required: String) =
        flowService.createFlow(CreateFlowRequest("flow-${System.nanoTime()}", "desc", required.toList()))

    private fun request(flowId: UUID, externalId: String? = null, name: String? = null) = CreateTrailRequest(
        flowId = flowId,
        gitCommitSha = "commit-sha",
        gitBranch = "main",
        gitAuthor = "a",
        gitAuthorEmail = "a@example.com",
        externalId = externalId,
        name = name
    )

    @Test
    fun `creating a trail twice with the same external id returns the same trail`() {
        val flow = newFlow("junit")
        val first = trailService.createTrail(request(flow.id, externalId = "release-2026.1"))
        val second = trailService.createTrail(request(flow.id, externalId = "release-2026.1"))

        assertEquals(first.id, second.id, "a re-run of the primary pipeline must not fork the evidence")
        assertEquals("release-2026.1", second.externalId)
    }

    @Test
    fun `the same external id under a different flow is a different trail`() {
        val flowA = newFlow("junit")
        val flowB = newFlow("junit")
        val a = trailService.createTrail(request(flowA.id, externalId = "release-1"))
        val b = trailService.createTrail(request(flowB.id, externalId = "release-1"))

        assertNotEquals(a.id, b.id)
    }

    @Test
    fun `trails without an external id are never deduplicated`() {
        val flow = newFlow("junit")
        val first = trailService.createTrail(request(flow.id))
        val second = trailService.createTrail(request(flow.id))

        assertNotEquals(first.id, second.id)
    }

    @Test
    fun `a trail can be resolved by external id`() {
        val flow = newFlow("junit")
        val created = trailService.createTrail(request(flow.id, externalId = "build-4711"))

        val found = trailService.lookupTrail(flow.id, externalId = "build-4711", name = null, gitCommitSha = null)

        assertEquals(created.id, found.id)
    }

    @Test
    fun `a trail can be resolved by name`() {
        val flow = newFlow("junit")
        val created = trailService.createTrail(request(flow.id, name = "nightly-release"))

        val found = trailService.lookupTrail(flow.id, externalId = null, name = "nightly-release", gitCommitSha = null)

        assertEquals(created.id, found.id)
    }

    @Test
    fun `resolving by commit sha returns the most recent trail for that commit`() {
        val flow = newFlow("junit")
        trailService.createTrail(request(flow.id, externalId = "run-1"))
        val latest = trailService.createTrail(request(flow.id, externalId = "run-2"))

        val found = trailService.lookupTrail(flow.id, externalId = null, name = null, gitCommitSha = "commit-sha")

        assertEquals(latest.id, found.id)
    }

    @Test
    fun `an unresolvable trail is a not-found, not a silently created one`() {
        val flow = newFlow("junit")

        assertThrows<NotFoundException> {
            trailService.lookupTrail(flow.id, externalId = "never-used", name = null, gitCommitSha = null)
        }
    }

    @Test
    fun `the CQRS command path is idempotent for an external id too`() {
        val flow = newFlow("junit")
        val command = CreateTrailCommand(
            flowId = flow.id,
            gitCommitSha = "commit-sha",
            gitBranch = "main",
            gitAuthor = "a",
            gitAuthorEmail = "a@example.com",
            externalId = "release-v2"
        )

        val first = trailCommandHandler.createTrail(command)
        val second = trailCommandHandler.createTrail(command)

        assertEquals(first.id, second.id)
        assertEquals("created", first.status)
        assertEquals("exists", second.status, "a reused trail must not be reported as newly created")
    }

    @Test
    fun `gates recorded by several pipelines aggregate onto one trail and assert together`() {
        val flow = newFlow("unit-tests", "integration-tests", "api-tests")

        // Primary pipeline creates the trail and records the gate it owns.
        val primary = trailService.createTrail(request(flow.id, externalId = "release-77"))
        attestationService.recordAttestation(
            primary.id,
            CreateAttestationRequest(type = "unit-tests", status = AttestationStatus.PASSED)
        )

        // A secondary pipeline resolves the same trail by the release identifier it was handed.
        val resolved: TrailResponse =
            trailService.lookupTrail(flow.id, externalId = "release-77", name = null, gitCommitSha = null)
        assertEquals(primary.id, resolved.id)
        attestationService.recordAttestation(
            resolved.id,
            CreateAttestationRequest(type = "integration-tests", status = AttestationStatus.PASSED)
        )

        // Still short of one gate: the trail must not assert compliant yet.
        assertEquals(
            ComplianceStatus.NON_COMPLIANT,
            assertService.assertTrail(primary.id, null, null).status
        )

        // The last pipeline reports.
        attestationService.recordAttestation(
            primary.id,
            CreateAttestationRequest(type = "api-tests", status = AttestationStatus.PASSED)
        )

        val result = assertService.assertTrail(primary.id, null, null)
        assertEquals(ComplianceStatus.COMPLIANT, result.status)
        assertEquals(primary.id, result.trailId)
    }
}
