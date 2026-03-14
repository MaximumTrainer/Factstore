package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.Policy
import com.factstore.core.port.outbound.IPolicyRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface PolicyRepositoryJpa : JpaRepository<Policy, UUID> {
    fun existsByName(name: String): Boolean
}

@Component
class PolicyRepositoryAdapter(private val jpa: PolicyRepositoryJpa) : IPolicyRepository {
    override fun save(policy: Policy): Policy = jpa.save(policy)
    override fun findById(id: UUID): Policy? = jpa.findById(id).orElse(null)
    override fun findAll(): List<Policy> = jpa.findAll()
    override fun existsById(id: UUID): Boolean = jpa.existsById(id)
    override fun existsByName(name: String): Boolean = jpa.existsByName(name)
    override fun deleteById(id: UUID) = jpa.deleteById(id)
}
