package com.factstore.adapter.inbound.web

import com.factstore.core.port.inbound.IUserService
import com.factstore.dto.CreateUserRequest
import com.factstore.dto.UpdateUserRequest
import com.factstore.dto.UserResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/users")
// #156 FR-5.5: this was unauthenticated CRUD, including DELETE. A user may read and update
// their own record; listing, creation, role changes and deletion require an administrator.
@Tag(name = "Users", description = "User management")
class UserController(private val userService: IUserService) {

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_admin')")
    @Operation(summary = "Create a new user")
    fun createUser(@RequestBody request: CreateUserRequest): ResponseEntity<UserResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(request))

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_admin')")
    @Operation(summary = "List all users")
    fun listUsers(): ResponseEntity<List<UserResponse>> =
        ResponseEntity.ok(userService.listUsers())

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_admin') or @userAccessPolicy.isSelf(#id)")
    @Operation(summary = "Get user by ID")
    fun getUser(@PathVariable id: UUID): ResponseEntity<UserResponse> =
        ResponseEntity.ok(userService.getUser(id))

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_admin') or @userAccessPolicy.isSelf(#id)")
    @Operation(summary = "Update a user")
    fun updateUser(@PathVariable id: UUID, @RequestBody request: UpdateUserRequest): ResponseEntity<UserResponse> =
        ResponseEntity.ok(userService.updateUser(id, request))

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_admin')")
    @Operation(summary = "Delete a user")
    fun deleteUser(@PathVariable id: UUID): ResponseEntity<Void> {
        userService.deleteUser(id)
        return ResponseEntity.noContent().build()
    }
}
