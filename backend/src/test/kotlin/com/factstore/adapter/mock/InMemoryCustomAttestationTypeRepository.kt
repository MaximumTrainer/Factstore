package com.factstore.adapter.mock

import com.factstore.core.domain.CustomAttestationType
import com.factstore.core.port.outbound.ICustomAttestationTypeRepository
import java.util.UUID

class InMemoryCustomAttestationTypeRepository : ICustomAttestationTypeRepository {
    private val store = mutableMapOf<UUID, CustomAttestationType>()

    override fun save(type: CustomAttestationType): CustomAttestationType {
        store[type.id] = type
        return type
    }

    override fun findById(id: UUID): CustomAttestationType? = store[id]

    override fun findByName(name: String): CustomAttestationType? =
        store.values.find { it.name == name }

    override fun findAllActive(): List<CustomAttestationType> =
        store.values.filter { it.archivedAt == null }

    override fun findAll(): List<CustomAttestationType> = store.values.toList()

    override fun existsByName(name: String): Boolean =
        store.values.any { it.name == name && it.archivedAt == null }
}
