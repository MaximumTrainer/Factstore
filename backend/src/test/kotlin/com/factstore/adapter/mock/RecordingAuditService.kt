package com.factstore.adapter.mock

import com.factstore.core.domain.AuditEventType
import com.factstore.core.port.inbound.IAuditService
import com.factstore.dto.AuditEventPage
import com.factstore.dto.AuditEventResponse
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

/**
 * In-memory [IAuditService] for unit tests that run without a Spring context.
 * Keeps every recorded event so a test can assert on what was written.
 */
class RecordingAuditService(private val objectMapper: ObjectMapper = ObjectMapper()) : IAuditService {

    val recorded = mutableListOf<AuditEventResponse>()

    override fun record(
        eventType: AuditEventType,
        actor: String,
        payload: Map<String, Any?>,
        trailId: UUID?,
        artifactSha256: String?,
        environmentId: UUID?
    ): AuditEventResponse {
        val event = AuditEventResponse(
            id = UUID.randomUUID(),
            eventType = eventType,
            environmentId = environmentId,
            trailId = trailId,
            artifactSha256 = artifactSha256,
            actor = actor,
            payload = objectMapper.writeValueAsString(payload),
            occurredAt = Instant.now()
        )
        recorded += event
        return event
    }

    /** The events of [eventType], most recent last. */
    fun eventsOfType(eventType: AuditEventType): List<AuditEventResponse> =
        recorded.filter { it.eventType == eventType }

    fun payloadOf(event: AuditEventResponse): Map<*, *> =
        objectMapper.readValue(event.payload, Map::class.java)

    override fun getEvent(id: UUID): AuditEventResponse =
        recorded.first { it.id == id }

    override fun queryEvents(
        eventType: AuditEventType?,
        trailId: UUID?,
        actor: String?,
        from: Instant?,
        to: Instant?,
        page: Int,
        size: Int,
        sortDesc: Boolean
    ): AuditEventPage {
        val matching = recorded.filter { event ->
            (eventType == null || event.eventType == eventType) &&
                (trailId == null || event.trailId == trailId) &&
                (actor == null || event.actor == actor)
        }
        return AuditEventPage(
            events = matching,
            page = page,
            size = size,
            totalElements = matching.size.toLong(),
            totalPages = 1
        )
    }

    override fun getEventsForTrail(trailId: UUID): List<AuditEventResponse> =
        recorded.filter { it.trailId == trailId }

    override fun exportEnvironmentAuditLogCsv(environmentId: UUID): String = ""
}
