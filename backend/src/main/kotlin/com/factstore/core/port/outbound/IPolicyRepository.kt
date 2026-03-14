package com.factstore.core.port.outbound

import com.factstore.core.domain.Policy
import java.util.UUID

interface IPolicyRepository {
    fun save(policy: Policy): Policy
    fun findById(id: UUID): Policy?
    fun findAll(): List<Policy>
    fun existsById(id: UUID): Boolean
    fun existsByName(name: String): Boolean
    fun deleteById(id: UUID)
}
