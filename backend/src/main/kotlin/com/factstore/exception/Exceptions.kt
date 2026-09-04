package com.factstore.exception

class NotFoundException(message: String) : RuntimeException(message)
class ConflictException(message: String) : RuntimeException(message)
class IntegrityException(message: String) : RuntimeException(message)
class BadRequestException(message: String) : RuntimeException(message)
class PullRequestNotFoundException(message: String) : RuntimeException(message)

/**
 * The caller is authenticated but not permitted (#155 FR-3.4, FR-4).
 *
 * Distinct from [NotFoundException]: a 403 says "you exist and may not do this", which is the
 * right answer for a scope the credential does not hold. Cross-*tenant* access is deliberately
 * a 404 instead, so probing cannot enumerate another organisation's resources (FR-5.2).
 */
class ForbiddenException(message: String) : RuntimeException(message)
