package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.Trail
import com.factstore.core.port.outbound.ITrailRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface TrailRepositoryJpa : JpaRepository<Trail, UUID> {
    fun findByFlowId(flowId: UUID): List<Trail>
}

@Component
class TrailRepositoryAdapter(private val jpa: TrailRepositoryJpa) : ITrailRepository {
    override fun save(trail: Trail): Trail = jpa.save(trail)
    override fun findById(id: UUID): Trail? = jpa.findById(id).orElse(null)
    override fun findAll(): List<Trail> = jpa.findAll()
    override fun existsById(id: UUID): Boolean = jpa.existsById(id)
    override fun findByFlowId(flowId: UUID): List<Trail> = jpa.findByFlowId(flowId)
}
