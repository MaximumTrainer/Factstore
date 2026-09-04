package com.factstore.application

import com.factstore.core.domain.AuditEventType
import com.factstore.core.domain.GateDecision
import com.factstore.core.port.outbound.IArtifactRepository
import com.factstore.core.port.outbound.IAuditEventRepository
import com.factstore.core.port.outbound.IDeploymentGateResultRepository
import com.factstore.core.port.outbound.IDeploymentRepository
import com.factstore.core.port.outbound.ITrailRepository
import com.factstore.dto.AssertionMetrics
import com.factstore.dto.CountedValue
import com.factstore.dto.DeliveryMetricsResponse
import com.factstore.dto.DoraMetric
import com.factstore.dto.GateDayBucket
import com.factstore.dto.GateMetrics
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * Delivery metrics for the dashboard (#151): DORA where Factstore's own records support it,
 * gate and assertion metrics throughout.
 *
 * The guiding rule is that a metric which cannot honestly be derived from what Factstore
 * records says so — [DoraMetric.available] false, with [DoraMetric.basis] explaining why —
 * rather than returning a plausible-looking zero. A zero on a dashboard is read as "we are
 * doing well"; an absent metric is read as "we do not know", which is the truth.
 */
@Service
@Transactional(readOnly = true)
class DeliveryMetricsService(
    private val deploymentRepository: IDeploymentRepository,
    private val gateResultRepository: IDeploymentGateResultRepository,
    private val artifactRepository: IArtifactRepository,
    private val trailRepository: ITrailRepository,
    private val auditEventRepository: IAuditEventRepository,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(DeliveryMetricsService::class.java)

    fun getDeliveryMetrics(days: Int): DeliveryMetricsResponse {
        val windowDays = days.coerceIn(MIN_WINDOW_DAYS, MAX_WINDOW_DAYS)
        val to = Instant.now()
        val from = to.minus(windowDays.toLong(), ChronoUnit.DAYS)

        val deployments = deploymentRepository.findByDeployedAtBetween(from, to)
        val gateResults = gateResultRepository.findAll()
            .filter { !it.evaluatedAt.isBefore(from) && !it.evaluatedAt.isAfter(to) }

        return DeliveryMetricsResponse(
            windowDays = windowDays,
            from = from,
            to = to,
            deploymentFrequency = deploymentFrequency(deployments.size, windowDays),
            leadTimeForChanges = leadTime(deployments),
            changeFailureRate = changeFailureRate(gateResults),
            timeToRestoreService = timeToRestore(),
            gates = gateMetrics(gateResults, from, windowDays),
            assertions = assertionMetrics(from, to)
        )
    }

    private fun deploymentFrequency(count: Int, windowDays: Int) = DoraMetric(
        value = if (count == 0) null else count.toDouble() / windowDays,
        unit = "per day",
        basis = "Deployments recorded to any environment, divided by the days in the window.",
        sampleSize = count,
        available = count > 0
    )

    /**
     * Commit-to-deploy, using the trail as the stand-in for the commit: a trail is created by the
     * pipeline run that builds the artifact, so its creation is the closest recorded moment to
     * "work started on this change". Deployments whose artifact has no trail contribute nothing.
     */
    private fun leadTime(deployments: List<com.factstore.core.domain.Deployment>): DoraMetric {
        val hours = deployments.mapNotNull { deployment ->
            val trail = artifactRepository.findBySha256Digest(deployment.artifactSha256)
                .mapNotNull { trailRepository.findById(it.trailId) }
                .minByOrNull { it.createdAt }
                ?: return@mapNotNull null
            val millis = deployment.deployedAt.toEpochMilli() - trail.createdAt.toEpochMilli()
            if (millis < 0) null else millis / 3_600_000.0
        }
        return DoraMetric(
            value = median(hours),
            unit = "hours",
            basis = "Median time from the creation of the trail that produced an artifact to that " +
                "artifact's deployment. Deployments whose artifact has no trail are excluded.",
            sampleSize = hours.size,
            available = hours.isNotEmpty()
        )
    }

    /**
     * Deliberately *not* presented as DORA's change failure rate, which measures releases that
     * degrade production. Factstore sees the gate, not the outcome, so this is the share of
     * releases stopped before reaching an environment — a leading indicator, and the basis says so.
     */
    private fun changeFailureRate(
        gateResults: List<com.factstore.core.domain.DeploymentGateResult>
    ): DoraMetric {
        val blocked = gateResults.count { it.decision == GateDecision.BLOCKED }
        return DoraMetric(
            value = if (gateResults.isEmpty()) null else percent(blocked, gateResults.size),
            unit = "percent",
            basis = "Share of deployment gate evaluations that blocked the release. This is a " +
                "pre-deployment gate rate, not DORA's post-release change failure rate: Factstore " +
                "records the gate decision, not what happened after a release shipped.",
            sampleSize = gateResults.size,
            available = gateResults.isNotEmpty()
        )
    }

    private fun timeToRestore() = DoraMetric(
        value = null,
        unit = "hours",
        basis = "Not derivable from Factstore data: restoring service is an incident-management " +
            "event, and no incident or outage records are kept here. Connect an incident source " +
            "to report this.",
        sampleSize = 0,
        available = false
    )

    private fun gateMetrics(
        gateResults: List<com.factstore.core.domain.DeploymentGateResult>,
        from: Instant,
        windowDays: Int
    ): GateMetrics {
        val allowed = gateResults.count { it.decision == GateDecision.ALLOWED }
        val blocked = gateResults.count { it.decision == GateDecision.BLOCKED }

        // Every day in the window gets a bucket, including empty ones, so a trend line does not
        // silently close the gaps where nothing shipped.
        val startDay = from.atZone(ZoneOffset.UTC).toLocalDate()
        val byDay = gateResults.groupBy { it.evaluatedAt.atZone(ZoneOffset.UTC).toLocalDate() }
        val perDay = (0 until windowDays).map { offset ->
            val day: LocalDate = startDay.plusDays(offset.toLong() + 1)
            val forDay = byDay[day].orEmpty()
            GateDayBucket(
                date = day.toString(),
                allowed = forDay.count { it.decision == GateDecision.ALLOWED },
                blocked = forDay.count { it.decision == GateDecision.BLOCKED }
            )
        }

        return GateMetrics(
            evaluations = gateResults.size,
            allowed = allowed,
            blocked = blocked,
            blockRate = if (gateResults.isEmpty()) 0.0 else percent(blocked, gateResults.size),
            topBlockReasons = rank(gateResults.flatMap { it.blockReasons }),
            perDay = perDay
        )
    }

    /**
     * Compliance assertions, read from the `GATE_ALLOWED` / `GATE_BLOCKED` audit events, which is
     * where every assertion outcome is already recorded.
     */
    private fun assertionMetrics(from: Instant, to: Instant): AssertionMetrics {
        val allowed = auditEventRepository.countWithFilters(
            eventType = AuditEventType.GATE_ALLOWED, from = from, to = to
        ).toInt()
        val blockedEvents = auditEventRepository.findWithFilters(
            eventType = AuditEventType.GATE_BLOCKED, from = from, to = to, page = 0, size = MAX_EVENTS
        )
        val missing = blockedEvents.flatMap { event ->
            runCatching {
                @Suppress("UNCHECKED_CAST")
                val payload = objectMapper.readValue(event.payload, Map::class.java) as Map<String, Any?>
                (payload["missingAttestationTypes"] as? List<*>).orEmpty().mapNotNull { it as? String } +
                    (payload["missingAttestationNames"] as? List<*>).orEmpty().mapNotNull { it as? String }
            }.onFailure { log.debug("Unreadable audit payload on event ${event.id}") }.getOrDefault(emptyList())
        }
        return AssertionMetrics(
            evaluations = allowed + blockedEvents.size,
            compliant = allowed,
            blocked = blockedEvents.size,
            blockRate = if (allowed + blockedEvents.size == 0) 0.0
            else percent(blockedEvents.size, allowed + blockedEvents.size),
            topMissingAttestations = rank(missing)
        )
    }

    private fun rank(values: List<String>): List<CountedValue> =
        values.groupingBy { it }.eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(TOP_N)
            .map { CountedValue(value = it.key, count = it.value) }

    private fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    private fun percent(part: Int, whole: Int): Double =
        Math.round(part.toDouble() / whole * 10_000) / 100.0

    private companion object {
        const val MIN_WINDOW_DAYS = 1
        const val MAX_WINDOW_DAYS = 365
        const val TOP_N = 5
        const val MAX_EVENTS = 1_000
    }
}
