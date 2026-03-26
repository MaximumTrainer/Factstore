package com.factstore.application

import com.factstore.core.domain.EventLogEntry
import com.factstore.core.domain.event.DomainEvent
import com.factstore.core.port.outbound.IEventStore
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Replays events from the Event Store to rebuild read-model state.
 *
 * In the current implementation the read-model tables are the same JPA
 * entities that the command side writes, so a full replay is equivalent to
 * verifying consistency.  Future iterations may project into separate
 * denormalised tables or external stores.
 */
@Service
class EventProjector(
    private val eventStore: IEventStore,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(EventProjector::class.java)

    private val eventTypeMap: Map<String, Class<out DomainEvent>> = mapOf(
        "FlowCreated" to DomainEvent.FlowCreated::class.java,
        "FlowUpdated" to DomainEvent.FlowUpdated::class.java,
        "FlowDeleted" to DomainEvent.FlowDeleted::class.java,
        "TrailCreated" to DomainEvent.TrailCreated::class.java,
        "ArtifactReported" to DomainEvent.ArtifactReported::class.java,
        "AttestationRecorded" to DomainEvent.AttestationRecorded::class.java,
        "EvidenceUploaded" to DomainEvent.EvidenceUploaded::class.java
    )

    /**
     * Replay every event in the store, invoking [handler] for each
     * deserialised [DomainEvent].  Returns the number of events processed.
     */
    fun replayAll(handler: (DomainEvent) -> Unit): Long {
        val entries = eventStore.findAll()
        entries.forEach { entry -> deserialize(entry)?.let(handler) }
        log.info("Replayed {} events from the event store", entries.size)
        return entries.size.toLong()
    }

    /**
     * Replay events whose sequence number is greater than [afterSequence].
     * Useful for incremental catch-up when the projector is running
     * continuously.
     */
    fun replayAfter(afterSequence: Long, handler: (DomainEvent) -> Unit): Long {
        val entries = eventStore.findAfterSequence(afterSequence)
        entries.forEach { entry -> deserialize(entry)?.let(handler) }
        log.info("Replayed {} incremental events (after seq {})", entries.size, afterSequence)
        return entries.size.toLong()
    }

    /**
     * Replay all events for a single aggregate.
     */
    fun replayAggregate(aggregateId: java.util.UUID, handler: (DomainEvent) -> Unit): Long {
        val entries = eventStore.findByAggregateId(aggregateId)
        entries.forEach { entry -> deserialize(entry)?.let(handler) }
        return entries.size.toLong()
    }

    private fun deserialize(entry: EventLogEntry): DomainEvent? {
        val clazz = eventTypeMap[entry.eventType]
        if (clazz == null) {
            log.warn("Unknown event type '{}' at sequence {}", entry.eventType, entry.sequenceNumber)
            return null
        }
        return try {
            objectMapper.readValue(entry.payload, clazz)
        } catch (e: Exception) {
            log.error("Failed to deserialize event seq={} type={}: {}", entry.sequenceNumber, entry.eventType, e.message)
            null
        }
    }
}
