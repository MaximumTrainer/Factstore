package com.factstore.core.port.outbound

import com.factstore.core.domain.ServiceAccount
import java.util.UUID

interface IServiceAccountRepository {
    fun save(serviceAccount: ServiceAccount): ServiceAccount
    fun findById(id: UUID): ServiceAccount?
    fun findAll(): List<ServiceAccount>
    fun existsById(id: UUID): Boolean
    fun existsByName(name: String): Boolean
    fun deleteById(id: UUID)
}
