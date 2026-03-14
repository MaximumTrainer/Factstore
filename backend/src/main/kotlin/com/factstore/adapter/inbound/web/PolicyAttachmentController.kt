package com.factstore.adapter.inbound.web

import com.factstore.core.port.inbound.IPolicyAttachmentService
import com.factstore.dto.CreatePolicyAttachmentRequest
import com.factstore.dto.PolicyAttachmentResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/policy-attachments")
@Tag(name = "Policy Attachments", description = "Policy attachment management")
class PolicyAttachmentController(private val policyAttachmentService: IPolicyAttachmentService) {

    @PostMapping
    @Operation(summary = "Attach a policy to an environment")
    fun createAttachment(@RequestBody request: CreatePolicyAttachmentRequest): ResponseEntity<PolicyAttachmentResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(policyAttachmentService.createAttachment(request))

    @GetMapping
    @Operation(summary = "List all policy attachments")
    fun listAttachments(): ResponseEntity<List<PolicyAttachmentResponse>> =
        ResponseEntity.ok(policyAttachmentService.listAttachments())

    @GetMapping("/{id}")
    @Operation(summary = "Get policy attachment by ID")
    fun getAttachment(@PathVariable id: UUID): ResponseEntity<PolicyAttachmentResponse> =
        ResponseEntity.ok(policyAttachmentService.getAttachment(id))

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove a policy attachment")
    fun deleteAttachment(@PathVariable id: UUID): ResponseEntity<Void> {
        policyAttachmentService.deleteAttachment(id)
        return ResponseEntity.noContent().build()
    }
}
