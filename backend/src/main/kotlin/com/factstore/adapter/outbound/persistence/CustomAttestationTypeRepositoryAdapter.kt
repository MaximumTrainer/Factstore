package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.CustomAttestationType
import com.factstore.core.port.outbound.ICustomAttestationTypeRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface CustomAttestationTypeRepositoryJpa : JpaRepository<CustomAttestationType, UUID> {
    fun findByName(name: String): CustomAttestationType?
    fun findAllByArchivedAtIsNull(): List<CustomAttestationType>
    fun existsByNameAndArchivedAtIsNull(name: String): Boolean
}

@Component
class CustomAttestationTypeRepositoryAdapter(
    private val jpa: CustomAttestationTypeRepositoryJpa
) : ICustomAttestationTypeRepository {
    override fun save(type: CustomAttestationType) = jpa.save(type)
    override fun findById(id: UUID) = jpa.findById(id).orElse(null)
    override fun findByName(name: String) = jpa.findByName(name)
    override fun findAllActive() = jpa.findAllByArchivedAtIsNull()
    override fun findAll() = jpa.findAll()
    override fun existsByName(name: String) = jpa.existsByNameAndArchivedAtIsNull(name)
}
