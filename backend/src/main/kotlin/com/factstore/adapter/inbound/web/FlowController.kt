package com.factstore.adapter.inbound.web

import com.factstore.application.DryRunContext
import com.factstore.core.port.inbound.IFlowService
import com.factstore.dto.CreateFlowRequest
import com.factstore.dto.DryRunResponse
import com.factstore.dto.FlowImpactResponse
import com.factstore.dto.FlowResponse
import com.factstore.dto.FlowTemplateResponse
import com.factstore.dto.RenameFlowRequest
import com.factstore.dto.UpdateFlowRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/flows")
@Tag(name = "Flows", description = "Flow management")
class FlowController(private val flowService: IFlowService) {

    @PostMapping
    @Operation(summary = "Create a new flow")
    fun createFlow(
        @RequestBody request: CreateFlowRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<*> {
        if (DryRunContext.isDryRun(httpRequest)) {
            val now = Instant.now()
            val wouldBe = FlowResponse(
                id = UUID.randomUUID(),
                name = request.name,
                description = request.description,
                requiredAttestationTypes = request.requiredAttestationTypes,
                tags = request.tags,
                orgSlug = request.orgSlug,
                templateYaml = request.templateYaml,
                createdAt = now,
                updatedAt = now,
                requiresApproval = request.requiresApproval,
                requiredApproverRoles = request.requiredApproverRoles
            )
            return ResponseEntity.ok(DryRunResponse(wouldCreate = wouldBe))
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(flowService.createFlow(request))
    }

    @GetMapping
    @Operation(summary = "List all flows")
    fun listFlows(
        @RequestParam(defaultValue = "false") includeArchived: Boolean
    ): ResponseEntity<List<FlowResponse>> =
        ResponseEntity.ok(flowService.listFlows(includeArchived))

    @GetMapping("/{id}")
    @Operation(summary = "Get flow by ID")
    fun getFlow(@PathVariable id: UUID): ResponseEntity<FlowResponse> =
        ResponseEntity.ok(flowService.getFlow(id))

    @PutMapping("/{id}")
    @Operation(summary = "Update a flow")
    fun updateFlow(@PathVariable id: UUID, @RequestBody request: UpdateFlowRequest): ResponseEntity<FlowResponse> =
        ResponseEntity.ok(flowService.updateFlow(id, request))

    @DeleteMapping("/{id}")
    @Operation(
        summary = "Delete a flow",
        description = "Refused with 409 when trails are still attached, unless force=true. " +
            "Archiving retires a flow without losing its history."
    )
    fun deleteFlow(
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "false") force: Boolean
    ): ResponseEntity<Void> {
        flowService.deleteFlow(id, force)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{id}/impact")
    @Operation(
        summary = "How much existing evidence a change to this flow would affect",
        description = "Changing the required attestations changes how every attached trail " +
            "evaluates on its next assert; this reports how many that is."
    )
    fun getFlowImpact(@PathVariable id: UUID): ResponseEntity<FlowImpactResponse> =
        ResponseEntity.ok(flowService.getFlowImpact(id))

    @GetMapping("/{id}/template")
    @Operation(summary = "Get flow template")
    fun getFlowTemplate(@PathVariable id: UUID): ResponseEntity<FlowTemplateResponse> =
        ResponseEntity.ok(flowService.getFlowTemplate(id))

    @PostMapping("/{id}/archive")
    @Operation(summary = "Archive a flow (soft delete)")
    fun archiveFlow(@PathVariable id: UUID): ResponseEntity<FlowResponse> =
        ResponseEntity.ok(flowService.archiveFlow(id))

    @PostMapping("/{id}/unarchive")
    @Operation(summary = "Unarchive a flow")
    fun unarchiveFlow(@PathVariable id: UUID): ResponseEntity<FlowResponse> =
        ResponseEntity.ok(flowService.unarchiveFlow(id))

    @PostMapping("/{id}/rename")
    @Operation(summary = "Rename a flow, preserving the old name for forwarding")
    fun renameFlow(
        @PathVariable id: UUID,
        @RequestBody request: RenameFlowRequest
    ): ResponseEntity<FlowResponse> =
        ResponseEntity.ok(flowService.renameFlow(id, request.newName))
}
