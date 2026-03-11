package com.factstore.core.port.inbound

import com.factstore.dto.ArtifactResponse
import com.factstore.dto.CreateArtifactRequest
import java.util.UUID

interface IArtifactService {
    fun reportArtifact(trailId: UUID, request: CreateArtifactRequest): ArtifactResponse
    fun listArtifactsForTrail(trailId: UUID): List<ArtifactResponse>
    fun findBySha256(sha256Digest: String): List<ArtifactResponse>
}
