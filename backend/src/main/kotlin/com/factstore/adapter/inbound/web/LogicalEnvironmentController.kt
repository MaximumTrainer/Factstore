package com.factstore.adapter.inbound.web

import com.factstore.core.port.inbound.ILogicalEnvironmentService
import com.factstore.dto.CreateLogicalEnvironmentRequest
import com.factstore.dto.LogicalEnvironmentResponse
import com.factstore.dto.UpdateLogicalEnvironmentRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/logical-environments")
@Tag(name = "Logical Environments", description = "Logical environment management")
class LogicalEnvironmentController(private val logicalEnvironmentService: ILogicalEnvironmentService) {

    @PostMapping
    @Operation(summary = "Create a new logical environment")
    fun createLogicalEnvironment(
        @RequestBody request: CreateLogicalEnvironmentRequest
    ): ResponseEntity<LogicalEnvironmentResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(logicalEnvironmentService.createLogicalEnvironment(request))

    @GetMapping
    @Operation(summary = "List all logical environments")
    fun listLogicalEnvironments(): ResponseEntity<List<LogicalEnvironmentResponse>> =
        ResponseEntity.ok(logicalEnvironmentService.listLogicalEnvironments())

    @GetMapping("/{id}")
    @Operation(summary = "Get logical environment by ID")
    fun getLogicalEnvironment(@PathVariable id: UUID): ResponseEntity<LogicalEnvironmentResponse> =
        ResponseEntity.ok(logicalEnvironmentService.getLogicalEnvironment(id))

    @PutMapping("/{id}")
    @Operation(summary = "Update a logical environment")
    fun updateLogicalEnvironment(
        @PathVariable id: UUID,
        @RequestBody request: UpdateLogicalEnvironmentRequest
    ): ResponseEntity<LogicalEnvironmentResponse> =
        ResponseEntity.ok(logicalEnvironmentService.updateLogicalEnvironment(id, request))

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a logical environment")
    fun deleteLogicalEnvironment(@PathVariable id: UUID): ResponseEntity<Void> {
        logicalEnvironmentService.deleteLogicalEnvironment(id)
        return ResponseEntity.noContent().build()
    }
}
