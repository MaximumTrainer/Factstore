package com.factstore.adapter.mock

import com.factstore.core.domain.AuditEvent
import com.factstore.core.domain.AuditEventType
import com.factstore.core.domain.Deployment
import com.factstore.core.domain.DeploymentGateResult
import com.factstore.core.port.outbound.IAuditEventRepository
import com.factstore.core.port.outbound.IDeploymentGateResultRepository
import com.factstore.core.port.outbound.IDeploymentRepository
import java.time.Instant
import java.util.UUID

/**
 * Fakes for the delivery metrics tests (#151).
 *
 * The metrics service reads *globally* — every deployment, gate result and audit event in the
 * window — so a Spring integration test over the shared H2 database cannot assert on totals or
 * on a top-N ranking: whatever the rest of the suite left behind lands in the same window.
 * These make the input exactly what the test sets up.
 */
class InMemoryDeploymentRepository : IDeploymentRepository {
    private val store = mutableListOf<Deployment>()

    override fun save(deployment: Deployment): Deployment {
        store += deployment
        return deployment
    }

    override fun findByArtifactSha256(sha256: String): List<Deployment> =
        store.filter { it.artifactSha256 == sha256 }

    override fun findByEnvironmentId(environmentId: UUID): List<Deployment> =
        store.filter { it.environmentId == environmentId }

    override fun existsByArtifactSha256AndEnvironmentId(sha256: String, environmentId: UUID): Boolean =
        store.any { it.artifactSha256 == sha256 && it.environmentId == environmentId }

    override fun findByDeployedAtBetween(from: Instant, to: Instant): List<Deployment> =
        store.filter { !it.deployedAt.isBefore(from) && !it.deployedAt.isAfter(to) }
}

class InMemoryDeploymentGateResultRepository : IDeploymentGateResultRepository {
    private val store = mutableListOf<DeploymentGateResult>()

    override fun save(result: DeploymentGateResult): DeploymentGateResult {
        store += result
        return result
    }

    override fun findAll(): List<DeploymentGateResult> = store.toList()

    override fun findByArtifactSha256(sha: String): List<DeploymentGateResult> =
        store.filter { it.artifactSha256 == sha }
}

class InMemoryAuditEventRepository : IAuditEventRepository {
    private val store = mutableListOf<AuditEvent>()

    override fun save(event: AuditEvent): AuditEvent {
        store += event
        return event
    }

    override fun findById(id: UUID): AuditEvent? = store.firstOrNull { it.id == id }

    override fun findByTrailId(trailId: UUID): List<AuditEvent> = store.filter { it.trailId == trailId }

    override fun findByEnvironmentId(environmentId: UUID): List<AuditEvent> =
        store.filter { it.environmentId == environmentId }

    override fun findWithFilters(
        eventType: AuditEventType?,
        trailId: UUID?,
        actor: String?,
        from: Instant?,
        to: Instant?,
        page: Int,
        size: Int,
        sortDesc: Boolean
    ): List<AuditEvent> = matching(eventType, trailId, actor, from, to)
        .sortedBy { it.occurredAt }
        .let { if (sortDesc) it.reversed() else it }
        .drop(page * size)
        .take(size)

    override fun countWithFilters(
        eventType: AuditEventType?,
        trailId: UUID?,
        actor: String?,
        from: Instant?,
        to: Instant?
    ): Long = matching(eventType, trailId, actor, from, to).size.toLong()

    private fun matching(
        eventType: AuditEventType?,
        trailId: UUID?,
        actor: String?,
        from: Instant?,
        to: Instant?
    ): List<AuditEvent> = store.filter { event ->
        (eventType == null || event.eventType == eventType) &&
            (trailId == null || event.trailId == trailId) &&
            (actor == null || event.actor == actor) &&
            (from == null || !event.occurredAt.isBefore(from)) &&
            (to == null || !event.occurredAt.isAfter(to))
    }
}
