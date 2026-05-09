package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.Flow
import com.factstore.core.port.outbound.IFlowRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface FlowRepositoryJpa : JpaRepository<Flow, UUID> {
    fun existsByName(name: String): Boolean
    fun findByName(name: String): Flow?
    fun findAllByOrgSlug(orgSlug: String): List<Flow>
    fun findAllByArchivedAtIsNull(): List<Flow>
    fun findByNameAndArchivedAtIsNull(name: String): Flow?
}

@Component
class FlowRepositoryAdapter(private val jpa: FlowRepositoryJpa) : IFlowRepository {
    override fun save(flow: Flow): Flow = jpa.save(flow)
    override fun findById(id: UUID): Flow? = jpa.findById(id).orElse(null)
    override fun findAll(): List<Flow> = jpa.findAll()
    override fun findAll(pageable: Pageable): Page<Flow> = jpa.findAll(pageable)
    override fun findAllByIds(ids: Collection<UUID>): List<Flow> = jpa.findAllById(ids)
    override fun existsById(id: UUID): Boolean = jpa.existsById(id)
    override fun existsByName(name: String): Boolean = jpa.findByNameAndArchivedAtIsNull(name) != null
    override fun deleteById(id: UUID) = jpa.deleteById(id)
    override fun countAll(): Long = jpa.count()
    override fun findAllByOrgSlug(orgSlug: String): List<Flow> = jpa.findAllByOrgSlug(orgSlug)
    override fun findAllActive(): List<Flow> = jpa.findAllByArchivedAtIsNull()
    override fun findByName(name: String): Flow? = jpa.findByNameAndArchivedAtIsNull(name)
    override fun findByNameOrPreviousName(name: String): Flow? =
        jpa.findAll().firstOrNull { flow ->
            flow.archivedAt == null &&
                (flow.name == name || flow.parsedPreviousNames.contains(name))
        }
}
