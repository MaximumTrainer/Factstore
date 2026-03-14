package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.LogicalEnvironment
import com.factstore.core.port.outbound.ILogicalEnvironmentRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface LogicalEnvironmentRepositoryJpa : JpaRepository<LogicalEnvironment, UUID> {
    fun existsByName(name: String): Boolean
}

@Component
class LogicalEnvironmentRepositoryAdapter(private val jpa: LogicalEnvironmentRepositoryJpa) : ILogicalEnvironmentRepository {
    override fun save(logicalEnvironment: LogicalEnvironment): LogicalEnvironment = jpa.save(logicalEnvironment)
    override fun findById(id: UUID): LogicalEnvironment? = jpa.findById(id).orElse(null)
    override fun findAll(): List<LogicalEnvironment> = jpa.findAll()
    override fun existsById(id: UUID): Boolean = jpa.existsById(id)
    override fun existsByName(name: String): Boolean = jpa.existsByName(name)
    override fun deleteById(id: UUID) = jpa.deleteById(id)
}
