package com.factstore.core.port.inbound

import com.factstore.dto.TrailCascadeCounts
import com.factstore.dto.TrailCleanupRequest
import com.factstore.dto.TrailCleanupResponse
import com.factstore.dto.TrailDeletionResponse
import com.factstore.dto.TrailResponse
import java.util.UUID

/**
 * Retiring obsolete trails (#161).
 *
 * Archiving is the default: a trail is compliance evidence, so hiding it from the listings is
 * almost always what is wanted, and it is reversible. Hard deletion exists for throwaway
 * evaluation data and is audited with the counts of everything it removed.
 */
interface ITrailCleanupService {
    fun archiveTrail(id: UUID): TrailResponse
    fun unarchiveTrail(id: UUID): TrailResponse
    /** What deleting this trail would remove, so a UI can state it before asking. */
    fun cascadeFor(id: UUID): TrailCascadeCounts
    fun deleteTrail(id: UUID): TrailDeletionResponse
    fun cleanup(request: TrailCleanupRequest): TrailCleanupResponse
}
