package com.factstore.core.port.inbound

import com.factstore.dto.CreateCustomAttestationTypeRequest
import com.factstore.dto.CustomAttestationTypeResponse
import com.factstore.dto.UpdateCustomAttestationTypeRequest
import java.util.UUID

interface ICustomAttestationTypeService {
    fun createType(request: CreateCustomAttestationTypeRequest): CustomAttestationTypeResponse
    fun listTypes(includeArchived: Boolean = false): List<CustomAttestationTypeResponse>
    fun getType(id: UUID): CustomAttestationTypeResponse
    fun updateType(id: UUID, request: UpdateCustomAttestationTypeRequest): CustomAttestationTypeResponse
    fun archiveType(id: UUID): CustomAttestationTypeResponse
    fun unarchiveType(id: UUID): CustomAttestationTypeResponse
}
