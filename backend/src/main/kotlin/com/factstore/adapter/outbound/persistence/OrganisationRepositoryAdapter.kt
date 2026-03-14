package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.Organisation
import com.factstore.core.port.outbound.IOrganisationRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface OrganisationRepositoryJpa : JpaRepository<Organisation, UUID> {
    fun existsBySlug(slug: String): Boolean
    fun findBySlug(slug: String): Organisation?
}

@Component
class OrganisationRepositoryAdapter(private val jpa: OrganisationRepositoryJpa) : IOrganisationRepository {
    override fun save(organisation: Organisation): Organisation = jpa.save(organisation)
    override fun findById(id: UUID): Organisation? = jpa.findById(id).orElse(null)
    override fun findBySlug(slug: String): Organisation? = jpa.findBySlug(slug)
    override fun findAll(): List<Organisation> = jpa.findAll()
    override fun existsById(id: UUID): Boolean = jpa.existsById(id)
    override fun existsBySlug(slug: String): Boolean = jpa.existsBySlug(slug)
    override fun deleteById(id: UUID) = jpa.deleteById(id)
}
