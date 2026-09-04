package com.factstore.core.port.outbound

import com.factstore.dto.TrailCascadeCounts
import java.util.UUID

/**
 * The write side of trail cleanup (#161).
 *
 * A trail owns attestations, artifacts, evidence files, approvals, coverage reports, security
 * scans, compliance assessments and Jira tickets — spread over tables with inconsistent
 * `ON DELETE` behaviour. Rather than widening eight repository ports with delete operations
 * that nothing else needs, the cascade lives behind this one port, so its order and its
 * boundaries are stated in a single place.
 *
 * Deliberately **not** cascaded:
 *  - `audit_events` — the record that evidence once existed must outlive the evidence.
 *  - the append-only ledger — entries are immutable by design.
 */
interface ITrailCleanupRepository {

    /** What a [deleteTrailCascade] would remove, without removing it. */
    fun countCascade(trailId: UUID): TrailCascadeCounts

    /** Removes the trail and everything it owns, returning what was removed. */
    fun deleteTrailCascade(trailId: UUID): TrailCascadeCounts
}
