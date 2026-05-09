package com.factstore.core.port.outbound

import com.factstore.core.domain.CustomAttestationType
import java.util.UUID

interface ICustomAttestationTypeRepository {
    fun save(type: CustomAttestationType): CustomAttestationType
    fun findById(id: UUID): CustomAttestationType?
    fun findByName(name: String): CustomAttestationType?
    fun findAllActive(): List<CustomAttestationType>
    fun findAll(): List<CustomAttestationType>
    fun existsByName(name: String): Boolean
}
