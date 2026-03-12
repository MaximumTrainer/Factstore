package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.Artifact
import com.factstore.core.port.outbound.IArtifactRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ArtifactRepositoryJpa : JpaRepository<Artifact, UUID> {
    fun findByTrailId(trailId: UUID): List<Artifact>
    fun findBySha256Digest(sha256Digest: String): List<Artifact>
    fun findBySha256DigestStartingWith(prefix: String): List<Artifact>
}

@Component
class ArtifactRepositoryAdapter(private val jpa: ArtifactRepositoryJpa) : IArtifactRepository {
    override fun save(artifact: Artifact): Artifact = jpa.save(artifact)
    override fun findByTrailId(trailId: UUID): List<Artifact> = jpa.findByTrailId(trailId)
    override fun findBySha256Digest(sha256Digest: String): List<Artifact> = jpa.findBySha256Digest(sha256Digest)
    override fun findBySha256DigestStartingWith(prefix: String): List<Artifact> = jpa.findBySha256DigestStartingWith(prefix)
}
