package com.factstore

import com.factstore.application.ArtifactService
import com.factstore.application.AttestationService
import com.factstore.application.DeliveryMetricsService
import com.factstore.application.FlowService
import com.factstore.core.domain.AttestationStatus
import com.factstore.core.domain.Deployment
import com.factstore.core.domain.GateDecision
import com.factstore.core.domain.DeploymentGateResult
import com.factstore.core.port.inbound.IAssertService
import com.factstore.core.port.inbound.ITrailService
import com.factstore.core.port.outbound.IDeploymentGateResultRepository
import com.factstore.core.port.outbound.IDeploymentRepository
import com.factstore.core.domain.Trail
import com.factstore.core.port.outbound.ITrailRepository
import com.factstore.dto.AssertRequest
import com.factstore.dto.CreateArtifactRequest
import com.factstore.dto.CreateAttestationRequest
import com.factstore.dto.CreateFlowRequest
import com.factstore.dto.CreateTrailRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * #151: a delivery metrics dashboard - DORA where the data supports it, gates throughout.
 *
 * The point of these tests is as much about honesty as arithmetic: a metric Factstore cannot
 * derive from what it records must say so rather than return a plausible-looking zero.
 */
@SpringBootTest
@Transactional
class DeliveryMetricsServiceTest {

    @Autowired lateinit var metricsService: DeliveryMetricsService
    @Autowired lateinit var flowService: FlowService
    @Autowired lateinit var trailService: ITrailService
    @Autowired lateinit var attestationService: AttestationService
    @Autowired lateinit var artifactService: ArtifactService
    @Autowired lateinit var assertService: IAssertService
    @Autowired lateinit var deploymentRepository: IDeploymentRepository
    @Autowired lateinit var gateResultRepository: IDeploymentGateResultRepository
    @Autowired lateinit var trailRepository: ITrailRepository

    private fun newFlowId(vararg required: String): UUID =
        flowService.createFlow(CreateFlowRequest("flow-${System.nanoTime()}", "d", required.toList())).id

    private fun newTrail(flowId: UUID) = trailService.createTrail(
        CreateTrailRequest(
            flowId = flowId,
            gitCommitSha = "sha-${System.nanoTime()}",
            gitBranch = "main",
            gitAuthor = "a",
            gitAuthorEmail = "a@example.com"
        )
    )

    private fun deploy(sha: String, at: Instant) {
        deploymentRepository.save(
            Deployment(
                artifactSha256 = sha,
                environmentId = UUID.randomUUID(),
                snapshotIndex = 1,
                deployedAt = at
            )
        )
    }

    private fun gate(sha: String, decision: GateDecision, at: Instant, reasons: List<String> = emptyList()) {
        gateResultRepository.save(
            DeploymentGateResult(
                artifactSha256 = sha,
                decision = decision,
                evaluatedAt = at
            ).also { it.blockReasons = reasons }
        )
    }

    // --- Deployment frequency ---------------------------------------------

    @Test
    fun `deployment frequency is deployments per day over the window`() {
        val now = Instant.now()
        repeat(7) { i -> deploy("sha256:df$i", now.minus(i.toLong(), ChronoUnit.DAYS)) }

        val metrics = metricsService.getDeliveryMetrics(days = 7)

        assertTrue(metrics.deploymentFrequency.available)
        assertEquals(7, metrics.deploymentFrequency.sampleSize)
        assertEquals(1.0, metrics.deploymentFrequency.value!!, 0.001)
        assertEquals("per day", metrics.deploymentFrequency.unit)
    }

    @Test
    fun `deployments outside the window are not counted`() {
        val now = Instant.now()
        deploy("sha256:inside", now.minus(1, ChronoUnit.DAYS))
        deploy("sha256:outside", now.minus(90, ChronoUnit.DAYS))

        val metrics = metricsService.getDeliveryMetrics(days = 7)

        assertEquals(1, metrics.deploymentFrequency.sampleSize)
    }

    @Test
    fun `with no deployments the metric is unavailable rather than zero`() {
        val metrics = metricsService.getDeliveryMetrics(days = 7)

        assertFalse(metrics.deploymentFrequency.available)
        assertNull(metrics.deploymentFrequency.value)
        assertEquals(0, metrics.deploymentFrequency.sampleSize)
    }

    // --- Lead time --------------------------------------------------------

    @Test
    fun `lead time is measured from the trail that produced the artifact to its deployment`() {
        val flowId = newFlowId("junit")
        // Backdate the trail by 3 hours so both it and the deployment sit inside the window.
        val trail = trailRepository.save(
            Trail(
                flowId = flowId,
                gitCommitSha = "sha-lt-${System.nanoTime()}",
                gitBranch = "main",
                gitAuthor = "a",
                gitAuthorEmail = "a@example.com",
                createdAt = Instant.now().minus(3, ChronoUnit.HOURS)
            )
        )
        val digest = "sha256:lt${System.nanoTime()}"
        artifactService.reportArtifact(trail.id, CreateArtifactRequest("img", "1", digest, reportedBy = "ci"))

        deploy(digest, Instant.now())

        val metrics = metricsService.getDeliveryMetrics(days = 7)

        assertTrue(metrics.leadTimeForChanges.available)
        assertEquals(1, metrics.leadTimeForChanges.sampleSize)
        assertEquals(3.0, metrics.leadTimeForChanges.value!!, 0.2)
        assertEquals("hours", metrics.leadTimeForChanges.unit)
    }

    @Test
    fun `a deployment whose artifact has no trail contributes no lead time`() {
        // An hour inside the window rather than exactly on its upper bound: a deployment
        // recorded in the same instant the window ends is an artificial case, and pinning
        // the test to that boundary made it sensitive to ordering under the full suite.
        deploy("sha256:orphan${System.nanoTime()}", Instant.now().minus(1, ChronoUnit.HOURS))

        val metrics = metricsService.getDeliveryMetrics(days = 7)

        assertFalse(metrics.leadTimeForChanges.available)
        assertEquals(0, metrics.leadTimeForChanges.sampleSize)
        assertTrue(metrics.deploymentFrequency.available, "the deployment itself still counts")
    }

    // --- Change failure rate ----------------------------------------------

    @Test
    fun `change failure rate is the share of gate evaluations that blocked`() {
        val now = Instant.now()
        gate("sha256:a", GateDecision.ALLOWED, now)
        gate("sha256:b", GateDecision.ALLOWED, now)
        gate("sha256:c", GateDecision.ALLOWED, now)
        gate("sha256:d", GateDecision.BLOCKED, now, listOf("missing snyk"))

        val metrics = metricsService.getDeliveryMetrics(days = 7)

        assertTrue(metrics.changeFailureRate.available)
        assertEquals(25.0, metrics.changeFailureRate.value!!, 0.001)
        assertEquals("percent", metrics.changeFailureRate.unit)
        assertEquals(4, metrics.changeFailureRate.sampleSize)
    }

    @Test
    fun `the change failure rate says what it actually measures`() {
        gate("sha256:a", GateDecision.ALLOWED, Instant.now())

        val basis = metricsService.getDeliveryMetrics(days = 7).changeFailureRate.basis.lowercase()

        // It is a pre-deployment gate rate, not DORA's post-release failure rate; the basis
        // has to be explicit or the number will be read as something it is not.
        assertTrue(basis.contains("gate"), "basis should name the gate: $basis")
    }

    // --- Time to restore --------------------------------------------------

    @Test
    fun `time to restore is reported as unavailable, with the reason, rather than fabricated`() {
        val metric = metricsService.getDeliveryMetrics(days = 30).timeToRestoreService

        assertFalse(metric.available)
        assertNull(metric.value)
        assertTrue(metric.basis.isNotBlank(), "an unavailable metric must explain itself")
    }

    // --- Gates ------------------------------------------------------------

    @Test
    fun `gate metrics count allowed and blocked evaluations`() {
        val now = Instant.now()
        gate("sha256:a", GateDecision.ALLOWED, now)
        gate("sha256:b", GateDecision.BLOCKED, now, listOf("missing snyk"))
        gate("sha256:c", GateDecision.BLOCKED, now, listOf("missing snyk", "approval required"))

        val gates = metricsService.getDeliveryMetrics(days = 7).gates

        assertEquals(3, gates.evaluations)
        assertEquals(1, gates.allowed)
        assertEquals(2, gates.blocked)
        assertEquals(66.67, gates.blockRate, 0.01)
    }

    @Test
    fun `the most common block reasons are ranked`() {
        val now = Instant.now()
        gate("sha256:a", GateDecision.BLOCKED, now, listOf("missing snyk"))
        gate("sha256:b", GateDecision.BLOCKED, now, listOf("missing snyk"))
        gate("sha256:c", GateDecision.BLOCKED, now, listOf("approval required"))

        val reasons = metricsService.getDeliveryMetrics(days = 7).gates.topBlockReasons

        assertEquals("missing snyk", reasons.first().value)
        assertEquals(2, reasons.first().count)
    }

    @Test
    fun `gate activity is bucketed by day for a trend line`() {
        val now = Instant.now()
        gate("sha256:a", GateDecision.ALLOWED, now)
        gate("sha256:b", GateDecision.BLOCKED, now)
        gate("sha256:c", GateDecision.ALLOWED, now.minus(2, ChronoUnit.DAYS))

        val perDay = metricsService.getDeliveryMetrics(days = 7).gates.perDay

        assertEquals(7, perDay.size, "every day in the window gets a bucket, including empty ones")
        assertEquals(1, perDay.last().allowed)
        assertEquals(1, perDay.last().blocked)
    }

    // --- Assertions -------------------------------------------------------

    @Test
    fun `assertion outcomes and the attestations that block them are reported`() {
        val flowId = newFlowId("junit", "snyk")
        val trail = newTrail(flowId)
        attestationService.recordAttestation(
            trail.id,
            CreateAttestationRequest(type = "junit", status = AttestationStatus.PASSED)
        )
        val digest = "sha256:as${System.nanoTime()}"
        artifactService.reportArtifact(trail.id, CreateArtifactRequest("img", "1", digest, reportedBy = "ci"))
        assertService.assertCompliance(AssertRequest(digest, flowId))

        val assertions = metricsService.getDeliveryMetrics(days = 7).assertions

        assertTrue(assertions.blocked >= 1)
        assertTrue(
            assertions.topMissingAttestations.any { it.value == "snyk" },
            "expected snyk to be reported as blocking, got ${assertions.topMissingAttestations}"
        )
    }

    // --- Window -----------------------------------------------------------

    @Test
    fun `the window is reported so a reader knows what the numbers cover`() {
        val metrics = metricsService.getDeliveryMetrics(days = 14)

        assertEquals(14, metrics.windowDays)
        assertNotNull(metrics.from)
        assertNotNull(metrics.to)
        assertTrue(metrics.from.isBefore(metrics.to))
    }

    @Test
    fun `an absurd window is clamped rather than accepted`() {
        assertEquals(1, metricsService.getDeliveryMetrics(days = 0).windowDays)
        assertEquals(365, metricsService.getDeliveryMetrics(days = 10_000).windowDays)
    }
}
