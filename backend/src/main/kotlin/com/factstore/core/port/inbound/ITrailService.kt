package com.factstore.core.port.inbound

import com.factstore.core.domain.Trail
import com.factstore.core.domain.TrailStatus
import com.factstore.dto.CreateTrailRequest
import com.factstore.dto.PageResponse
import com.factstore.dto.TrailResponse
import java.util.UUID

/** Outcome of a get-or-create; [created] is false when an existing trail was reused (#164). */
data class TrailCreationResult(val trail: TrailResponse, val created: Boolean)

interface ITrailService {
    fun createTrail(request: CreateTrailRequest): TrailResponse
    fun createOrGetTrail(request: CreateTrailRequest): TrailCreationResult
    fun lookupTrail(flowId: UUID, externalId: String?, name: String?, gitCommitSha: String?): TrailResponse
    /** Archived trails are hidden unless [includeArchived] (#161). */
    fun listTrails(flowId: UUID?, includeArchived: Boolean = false): List<TrailResponse>
    fun getTrail(id: UUID): TrailResponse
    fun listTrailsForFlow(flowId: UUID, includeArchived: Boolean = false): List<TrailResponse>
    fun listTrailsForFlow(flowId: UUID, page: Int, size: Int, includeArchived: Boolean = false): PageResponse<TrailResponse>
    fun updateTrailStatus(id: UUID, status: TrailStatus): Trail
    fun getTrailEntity(id: UUID): Trail
    fun findByName(flowId: UUID, name: String): TrailResponse
}
