package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.ServiceAccount
import com.factstore.core.port.outbound.IServiceAccountRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ServiceAccountRepositoryJpa : JpaRepository<ServiceAccount, UUID> {
    fun existsByName(name: String): Boolean
}

@Component
class ServiceAccountRepositoryAdapter(private val jpa: ServiceAccountRepositoryJpa) : IServiceAccountRepository {
    override fun save(serviceAccount: ServiceAccount): ServiceAccount = jpa.save(serviceAccount)
    override fun findById(id: UUID): ServiceAccount? = jpa.findById(id).orElse(null)
    override fun findAll(): List<ServiceAccount> = jpa.findAll()
    override fun existsById(id: UUID): Boolean = jpa.existsById(id)
    override fun existsByName(name: String): Boolean = jpa.existsByName(name)
    override fun deleteById(id: UUID) = jpa.deleteById(id)
}
