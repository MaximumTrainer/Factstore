package com.factstore.exception

import com.factstore.dto.ErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(ex: NotFoundException): ResponseEntity<ErrorResponse> {
        log.warn("Not found: ${ex.message}")
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(404, "Not Found", ex.message ?: "Resource not found"))
    }

    @ExceptionHandler(ConflictException::class)
    fun handleConflict(ex: ConflictException): ResponseEntity<ErrorResponse> {
        log.warn("Conflict: ${ex.message}")
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse(409, "Conflict", ex.message ?: "Conflict"))
    }

    @ExceptionHandler(ForbiddenException::class)
    fun handleForbidden(ex: ForbiddenException): ResponseEntity<ErrorResponse> {
        log.warn("Forbidden: ${ex.message}")
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(403, "Forbidden", ex.message ?: "Not permitted"))
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException::class)
    fun handleAccessDenied(
        ex: org.springframework.security.access.AccessDeniedException
    ): ResponseEntity<ErrorResponse> {
        // Raised by @PreAuthorize. Reported as 403 rather than 500, and without echoing the
        // expression that failed.
        log.warn("Access denied: ${ex.message}")
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(403, "Forbidden", "You do not have permission to perform this action"))
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMessageNotReadable(ex: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
        log.warn("Malformed request body: ${ex.message}")
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(400, "Bad Request", "Malformed or unreadable request body"))
    }

    @ExceptionHandler(BadRequestException::class)
    fun handleBadRequest(ex: BadRequestException): ResponseEntity<ErrorResponse> {
        log.warn("Bad request: ${ex.message}")
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(400, "Bad Request", ex.message ?: "Bad request"))
    }

    @ExceptionHandler(IntegrityException::class)
    fun handleIntegrity(ex: IntegrityException): ResponseEntity<ErrorResponse> {
        log.error("Integrity error: ${ex.message}")
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(500, "Integrity Error", ex.message ?: "Data integrity error"))
    }

    @ExceptionHandler(PullRequestNotFoundException::class)
    fun handlePrNotFound(ex: PullRequestNotFoundException): ResponseEntity<ErrorResponse> {
        log.warn("Pull request not found: ${ex.message}")
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ErrorResponse(422, "Unprocessable Entity", ex.message ?: "No pull request found for commit"))
    }

    /**
     * Bean-validation failures on a `@Valid @RequestBody`.
     *
     * Without this, they fell through to the catch-all below and every one came back as a
     * **500** — so a client that sent a bad field could not tell its own mistake from the
     * server breaking, and a CI pipeline retried a request that would never succeed. The
     * offending fields are named, because "Bad Request" with nothing else is barely better
     * than the 500 it replaces.
     */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val details = ex.bindingResult.fieldErrors.joinToString("; ") {
            "${it.field}: ${it.defaultMessage ?: "is invalid"}"
        }.ifBlank { "Request validation failed" }
        log.warn("Validation failed: $details")
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(400, "Bad Request", details))
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneral(ex: Exception): ResponseEntity<ErrorResponse> {
        log.error("Unexpected error", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(500, "Internal Server Error", ex.message ?: "An unexpected error occurred"))
    }
}
