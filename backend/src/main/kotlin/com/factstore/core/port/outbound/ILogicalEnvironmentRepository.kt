package com.factstore.core.port.outbound

import com.factstore.core.domain.LogicalEnvironment
import java.util.UUID

interface ILogicalEnvironmentRepository {
    fun save(logicalEnvironment: LogicalEnvironment): LogicalEnvironment
    fun findById(id: UUID): LogicalEnvironment?
    fun findAll(): List<LogicalEnvironment>
    fun existsById(id: UUID): Boolean
    fun existsByName(name: String): Boolean
    fun deleteById(id: UUID)
}
