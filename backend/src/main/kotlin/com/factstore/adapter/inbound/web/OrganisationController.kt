package com.factstore.adapter.inbound.web

import com.factstore.core.port.inbound.IOrganisationService
import com.factstore.dto.CreateOrganisationRequest
import com.factstore.dto.OrganisationResponse
import com.factstore.dto.UpdateOrganisationRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/organisations")
@Tag(name = "Organisations", description = "Organisation management")
class OrganisationController(private val organisationService: IOrganisationService) {

    @PostMapping
    @Operation(summary = "Create a new organisation")
    fun createOrganisation(@RequestBody request: CreateOrganisationRequest): ResponseEntity<OrganisationResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(organisationService.createOrganisation(request))

    @GetMapping
    @Operation(summary = "List all organisations")
    fun listOrganisations(): ResponseEntity<List<OrganisationResponse>> =
        ResponseEntity.ok(organisationService.listOrganisations())

    @GetMapping("/{id}")
    @Operation(summary = "Get organisation by ID")
    fun getOrganisation(@PathVariable id: UUID): ResponseEntity<OrganisationResponse> =
        ResponseEntity.ok(organisationService.getOrganisation(id))

    @PutMapping("/{id}")
    @Operation(summary = "Update an organisation")
    fun updateOrganisation(
        @PathVariable id: UUID,
        @RequestBody request: UpdateOrganisationRequest
    ): ResponseEntity<OrganisationResponse> =
        ResponseEntity.ok(organisationService.updateOrganisation(id, request))

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an organisation")
    fun deleteOrganisation(@PathVariable id: UUID): ResponseEntity<Void> {
        organisationService.deleteOrganisation(id)
        return ResponseEntity.noContent().build()
    }
}
