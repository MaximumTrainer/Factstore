package com.factstore.adapter.inbound.web

import com.factstore.core.port.inbound.IFlowService
import com.factstore.core.port.inbound.IOrganisationService
import com.factstore.dto.CreateOrganisationRequest
import com.factstore.dto.FlowResponse
import com.factstore.dto.OrganisationResponse
import com.factstore.dto.UpdateOrganisationRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/organisations")
@Tag(name = "Organisations", description = "Organisation management")
class OrganisationController(
    private val organisationService: IOrganisationService,
    private val flowService: IFlowService
) {

    @PostMapping
    @Operation(summary = "Create a new organisation")
    fun createOrganisation(@RequestBody request: CreateOrganisationRequest): ResponseEntity<OrganisationResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(organisationService.createOrganisation(request))

    @GetMapping
    @Operation(summary = "List all organisations")
    fun listOrganisations(): ResponseEntity<List<OrganisationResponse>> =
        ResponseEntity.ok(organisationService.listOrganisations())

    @GetMapping("/{slug}")
    @Operation(summary = "Get organisation by slug")
    fun getOrganisationBySlug(@PathVariable slug: String): ResponseEntity<OrganisationResponse> =
        ResponseEntity.ok(organisationService.getOrganisationBySlug(slug))

    @PutMapping("/{slug}")
    @Operation(summary = "Update an organisation")
    fun updateOrganisationBySlug(
        @PathVariable slug: String,
        @RequestBody request: UpdateOrganisationRequest
    ): ResponseEntity<OrganisationResponse> =
        ResponseEntity.ok(organisationService.updateOrganisationBySlug(slug, request))

    @DeleteMapping("/{slug}")
    @Operation(summary = "Delete an organisation")
    fun deleteOrganisationBySlug(@PathVariable slug: String): ResponseEntity<Void> {
        organisationService.deleteOrganisationBySlug(slug)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{slug}/flows")
    @Operation(summary = "List flows in an organisation")
    fun listFlowsByOrg(@PathVariable slug: String): ResponseEntity<List<FlowResponse>> =
        ResponseEntity.ok(flowService.listFlowsByOrg(slug))
}
