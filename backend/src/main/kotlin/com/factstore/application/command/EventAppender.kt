package com.factstore.application.command

import com.factstore.core.domain.EventLogEntry
import com.factstore.core.domain.event.DomainEvent
import com.factstore.core.port.outbound.IDomainEventBus
import com.factstore.core.port.outbound.IEventStore
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * Converts a [DomainEvent] into an [EventLogEntry] and appends it to the
 * event store.  After the surrounding transaction **commits**, the entry
 * is published to the [IDomainEventBus] so that the query service can
 * project it into the read database.
 *
 * Publishing is deferred via [TransactionSynchronizationManager] to
 * prevent phantom events:  if the transaction rolls back, the RabbitMQ
 * message is never sent, keeping the write and read sides consistent.
 */
@Component
class EventAppender(
    private val eventStore: IEventStore,
    private val objectMapper: ObjectMapper,
    private val domainEventBus: IDomainEventBus
) {
    fun append(event: DomainEvent) {
        val entry = EventLogEntry(
            eventId = event.eventId,
            aggregateId = event.aggregateId,
            aggregateType = event.aggregateType,
            eventType = event::class.simpleName ?: "Unknown",
            payload = objectMapper.writeValueAsString(event),
            occurredAt = event.occurredAt
        )
        val saved = eventStore.append(entry)

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() {
                    domainEventBus.publish(saved)
                }
            })
        } else {
            // No active transaction (e.g. in unit tests) — publish immediately.
            domainEventBus.publish(saved)
        }
    }
}
