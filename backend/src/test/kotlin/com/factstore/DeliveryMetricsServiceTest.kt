package com.factstore

import com.factstore.adapter.mock.InMemoryArtifactRepository
import com.factstore.adapter.mock.InMemoryAuditEventRepository
import com.factstore.adapter.mock.InMemoryDeploymentGateResultRepository
import com.factstore.adapter.mock.InMemoryDeploymentRepository
import com.factstore.adapter.mock.InMemoryTrailRepository
import com.factstore.application.DeliveryMetricsService
import com.factstore.core.domain.Artifact
import com.factstore.core.domain.AuditEvent
import com.factstore.core.domain.AuditEventType
import com.factstore.core.domain.Deployment
import com.factstore.core.domain.DeploymentGateResult
import com.factstore.core.domain.GateDecision
import com.factstore.core.domain.Trail
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * #151: a delivery metrics dashboard — DORA where the data supports it, gates throughout.
 *
 * These run without a Spring context, over in-memory repositories. The service reads
 * *globally* — every deployment, gate result and audit event in the window — so on a shared
 * database totals and top-N rankings depend on whatever the rest of the suite left behind.
 * That is a property of the service, not a bug, but it means these assertions are only
 * meaningful when the test owns its input.
 *
 * The point of the tests is as much about honesty as arithmetic: a metric Factstore cannot
 * derive from what it records must say so rather than return a plausible-looking zero.
 */
class DeliveryMetricsServiceTest {

    private lateinit var deployments: InMemoryDeploymentRepository
    private lateinit var gateResults: InMemoryDeploymentGateResultRepository
    private lateinit var artifacts: InMemoryArtifactRepository
    private lateinit var trails: InMemoryTrailRepository
    private lateinit var auditEvents: InMemoryAuditEventRepository
    private lateinit var metricsService: DeliveryMetricsService

    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        deployments = InMemoryDeploymentRepository()
        gateResults = InMemoryDeploymentGateResultRepository()
        artifacts = InMemoryArtifactRepository()
        trails = InMemoryTrailRepository()
        auditEvents = InMemoryAuditEventRepository()
        metricsService = DeliveryMetricsService(
            deployments, gateResults, artifacts, trails, auditEvents, objectMapper
        )
    }

    private fun deploy(sha: String, at: Instant) {
        deployments.save(
            Deployment(
                artifactSha256 = sha,
                environmentId = UUID.randomUUID(),
                snapshotIndex = 1,
                deployedAt = at
            )
        )
    }

    private fun gate(sha: String, decision: GateDecision, at: Instant, reasons: List<String> = emptyList()) {
        gateResults.save(
            DeploymentGateResult(artifactSha256 = sha, decision = decision, evaluatedAt = at)
                .also { it.blockReasons = reasons }
        )
    }

    /** A trail that produced [sha], created [hoursAgo] hours ago. */
    private fun trailProducing(sha: String, hoursAgo: Long) {
        val trail = trails.save(
            Trail(
                flowId = UUID.randomUUID(),
                gitCommitSha = "sha-${System.nanoTime()}",
                gitBranch = "main",
                gitAuthor = "a",
                gitAuthorEmail = "a@example.com",
                createdAt = Instant.now().minus(hoursAgo, ChronoUnit.HOURS)
            )
        )
        artifacts.save(
            Artifact(
                trailId = trail.id,
                imageName = "img",
                imageTag = "1",
                sha256Digest = sha,
                reportedBy = "ci"
            )
        )
    }

    private fun blockedAssertion(missing: List<String>, at: Instant = Instant.now()) {
        auditEvents.save(
            AuditEvent(
                eventType = AuditEventType.GATE_BLOCKED,
                actor = "system",
                payload = objectMapper.writeValueAsString(mapOf("missingAttestationTypes" to missing)),
                occurredAt = at
            )
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

        assertEquals(1, metricsService.getDeliveryMetrics(days = 7).deploymentFrequency.sampleSize)
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
        val digest = "sha256:lt"
        trailProducing(digest, hoursAgo = 3)
        deploy(digest, Instant.now())

        val metrics = metricsService.getDeliveryMetrics(days = 7)

        assertTrue(metrics.leadTimeForChanges.available)
        assertEquals(1, metrics.leadTimeForChanges.sampleSize)
        assertEquals(3.0, metrics.leadTimeForChanges.value!!, 0.1)
        assertEquals("hours", metrics.leadTimeForChanges.unit)
    }

    @Test
    fun `lead time is the median, so one slow release does not dominate`() {
        listOf(1L, 2L, 30L).forEachIndexed { i, hours ->
            val digest = "sha256:median$i"
            trailProducing(digest, hoursAgo = hours)
            deploy(digest, Instant.now())
        }

        val metrics = metricsService.getDeliveryMetrics(days = 7)

        assertEquals(3, metrics.leadTimeForChanges.sampleSize)
        assertEquals(2.0, metrics.leadTimeForChanges.value!!, 0.1)
    }

    @Test
    fun `a deployment whose artifact has no trail contributes no lead time`() {
        deploy("sha256:orphan", Instant.now().minus(1, ChronoUnit.HOURS))

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
        assertTrue(basis.contains("not dora"), "basis should disclaim the DORA reading: $basis")
    }

    @Test
    fun `with no gate evaluations the failure rate is unavailable, not zero percent`() {
        val metric = metricsService.getDeliveryMetrics(days = 7).changeFailureRate

        assertFalse(metric.available)
        assertNull(metric.value)
    }

    // --- Time to restore --------------------------------------------------

    @Test
    fun `time to restore is reported as unavailable, with the reason, rather than fabricated`() {
        val metric = metricsService.getDeliveryMetrics(days = 30).timeToRestoreService

        assertFalse(metric.available)
        assertNull(metric.value)
        assertTrue(metric.basis.isNotBlank(), "an unavailable metric must explain itself")
        assertTrue(metric.basis.lowercase().contains("incident"))
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
        blockedAssertion(listOf("snyk", "junit"))
        blockedAssertion(listOf("snyk"))
        auditEvents.save(
            AuditEvent(
                eventType = AuditEventType.GATE_ALLOWED,
                actor = "system",
                payload = "{}",
                occurredAt = Instant.now()
            )
        )

        val assertions = metricsService.getDeliveryMetrics(days = 7).assertions

        assertEquals(3, assertions.evaluations)
        assertEquals(1, assertions.compliant)
        assertEquals(2, assertions.blocked)
        assertEquals("snyk", assertions.topMissingAttestations.first().value)
        assertEquals(2, assertions.topMissingAttestations.first().count)
    }

    @Test
    fun `an unreadable audit payload does not break the ranking`() {
        auditEvents.save(
            AuditEvent(
                eventType = AuditEventType.GATE_BLOCKED,
                actor = "system",
                payload = "not-json",
                occurredAt = Instant.now()
            )
        )
        blockedAssertion(listOf("snyk"))

        val assertions = metricsService.getDeliveryMetrics(days = 7).assertions

        assertEquals(2, assertions.blocked)
        assertEquals("snyk", assertions.topMissingAttestations.single().value)
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
