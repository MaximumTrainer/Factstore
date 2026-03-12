package com.factstore.application

import com.factstore.core.domain.User
import com.factstore.core.port.inbound.IUserService
import com.factstore.core.port.outbound.IUserRepository
import com.factstore.dto.CreateUserRequest
import com.factstore.dto.UpdateUserRequest
import com.factstore.dto.UserResponse
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class UserService(private val userRepository: IUserRepository) : IUserService {

    private val log = LoggerFactory.getLogger(UserService::class.java)

    override fun createUser(request: CreateUserRequest): UserResponse {
        if (userRepository.existsByEmail(request.email)) {
            throw ConflictException("User with email '${request.email}' already exists")
        }
        if (request.githubId != null && userRepository.findByGithubId(request.githubId) != null) {
            throw ConflictException("User with GitHub ID '${request.githubId}' already exists")
        }
        val user = User(
            email = request.email,
            name = request.name,
            githubId = request.githubId
        )
        val saved = userRepository.save(user)
        log.info("Created user: ${saved.id} email=${saved.email}")
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    override fun listUsers(): List<UserResponse> = userRepository.findAll().map { it.toResponse() }

    @Transactional(readOnly = true)
    override fun getUser(id: UUID): UserResponse =
        (userRepository.findById(id) ?: throw NotFoundException("User not found: $id")).toResponse()

    override fun updateUser(id: UUID, request: UpdateUserRequest): UserResponse {
        val user = userRepository.findById(id) ?: throw NotFoundException("User not found: $id")
        request.name?.let { user.name = it }
        request.githubId?.let { user.githubId = it }
        user.updatedAt = Instant.now()
        return userRepository.save(user).toResponse()
    }

    override fun deleteUser(id: UUID) {
        if (!userRepository.existsById(id)) throw NotFoundException("User not found: $id")
        userRepository.deleteById(id)
        log.info("Deleted user: $id")
    }

    override fun findOrCreateByGithub(githubId: String, email: String, name: String): UserResponse {
        // 1. Try to resolve by GitHub ID first – this is the canonical link.
        val existingByGithub = userRepository.findByGithubId(githubId)
        if (existingByGithub != null) {
            return existingByGithub.toResponse()
        }

        // 2. Fall back to lookup by email for potential first-time GitHub linking.
        val existingByEmail = userRepository.findByEmail(email)
        if (existingByEmail != null) {
            when {
                existingByEmail.githubId == null -> {
                    // Link this GitHub account to the existing user.
                    existingByEmail.githubId = githubId
                    existingByEmail.updatedAt = Instant.now()
                    userRepository.save(existingByEmail)
                    return existingByEmail.toResponse()
                }
                existingByEmail.githubId == githubId -> {
                    // Idempotent case: email is already linked to this GitHub account.
                    return existingByEmail.toResponse()
                }
                else -> {
                    // Security-sensitive: email is already linked to a different GitHub account.
                    log.warn(
                        "GitHub OAuth conflict for email={} incomingGithubId={} existingGithubId={}",
                        email, githubId, existingByEmail.githubId
                    )
                    throw ConflictException(
                        "User with email '$email' is already linked to a different GitHub account"
                    )
                }
            }
        }

        // 3. No matching user – create a new one linked to this GitHub account.
        val user = User(email = email, name = name, githubId = githubId)
        val saved = userRepository.save(user)
        log.info("Created user via GitHub OAuth: ${saved.id} githubId=$githubId")
        return saved.toResponse()
    }

    override fun getUserEntity(id: UUID): User =
        userRepository.findById(id) ?: throw NotFoundException("User not found: $id")
}

fun User.toResponse() = UserResponse(
    id = id,
    email = email,
    name = name,
    githubId = githubId,
    createdAt = createdAt,
    updatedAt = updatedAt
)
