package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * Append-only event log entry. Each row is an immutable record of a domain
 * event that was emitted as part of a state transition.
 *
 * In production the `sequence_number` column is a database-generated IDENTITY
 * (see V33 migration). In H2 test environments Hibernate's DDL auto-generation
 * creates it as a regular column and ordering falls back to [occurredAt].
 */
@Entity
@Table(name = "domain_events")
class EventLogEntry(
    @Id
    @Column(name = "event_id")
    val eventId: UUID = UUID.randomUUID(),

    @Column(name = "aggregate_id", nullable = false)
    val aggregateId: UUID,

    @Column(name = "aggregate_type", nullable = false, length = 64)
    val aggregateType: String,

    @Column(name = "event_type", nullable = false, length = 128)
    val eventType: String,

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    val payload: String,

    @Column(name = "metadata", columnDefinition = "TEXT")
    val metadata: String? = null,

    @Column(name = "occurred_at", nullable = false)
    val occurredAt: Instant = Instant.now(),

    @Column(name = "sequence_number")
    var sequenceNumber: Long? = null
)
