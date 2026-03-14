package com.factstore.core.port.inbound

import com.factstore.dto.CreateOrganisationRequest
import com.factstore.dto.OrganisationResponse
import com.factstore.dto.UpdateOrganisationRequest
import java.util.UUID

interface IOrganisationService {
    fun createOrganisation(request: CreateOrganisationRequest): OrganisationResponse
    fun listOrganisations(): List<OrganisationResponse>
    fun getOrganisation(id: UUID): OrganisationResponse
    fun updateOrganisation(id: UUID, request: UpdateOrganisationRequest): OrganisationResponse
    fun deleteOrganisation(id: UUID)
    fun getOrganisationBySlug(slug: String): OrganisationResponse
    fun updateOrganisationBySlug(slug: String, request: UpdateOrganisationRequest): OrganisationResponse
    fun deleteOrganisationBySlug(slug: String)
}
