package com.factstore.core.port.outbound

import com.factstore.core.domain.Organisation
import java.util.UUID

interface IOrganisationRepository {
    fun save(organisation: Organisation): Organisation
    fun findById(id: UUID): Organisation?
    fun findBySlug(slug: String): Organisation?
    fun findAll(): List<Organisation>
    fun existsById(id: UUID): Boolean
    fun existsBySlug(slug: String): Boolean
    fun deleteById(id: UUID)
}
