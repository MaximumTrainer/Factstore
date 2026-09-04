package com.factstore.pact

import au.com.dius.pact.provider.junit5.HttpTestTarget
import au.com.dius.pact.provider.junit5.PactVerificationContext
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider
import au.com.dius.pact.provider.junitsupport.Provider
import au.com.dius.pact.provider.junitsupport.State
import au.com.dius.pact.provider.junitsupport.loader.PactFolder
import com.factstore.adapter.outbound.persistence.FlowRepositoryJpa
import com.factstore.adapter.outbound.persistence.TrailRepositoryJpa
import com.factstore.adapter.inbound.web.ApiKeyAuthFilter
import com.factstore.application.ApiKeyService
import com.factstore.core.domain.Flow
import com.factstore.core.domain.OwnerType
import com.factstore.core.domain.User
import com.factstore.core.domain.security.Permission
import com.factstore.core.port.outbound.IUserRepository
import com.factstore.dto.CreateApiKeyRequest
import org.apache.hc.core5.http.HttpRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestTemplate
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.util.UUID

@ExtendWith(SpringExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Provider("factstore-backend")
@PactFolder("../pacts")
class PactProviderVerificationTest {

    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var flowRepository: FlowRepositoryJpa

    @Autowired
    lateinit var trailRepository: TrailRepositoryJpa

    @Autowired
    lateinit var apiKeyService: ApiKeyService

    @Autowired
    lateinit var userRepository: IUserRepository

    /**
     * A real credential for the verification run.
     *
     * The consumer contracts were written against an API that accepted anything, so a
     * mutating interaction now needs a key with the right scopes. Minting one here keeps the
     * contracts describing *behaviour* rather than being quietly weakened to describe an
     * unauthenticated API that no longer exists.
     */
    private lateinit var verificationKey: String

    @BeforeEach
    fun setup(context: PactVerificationContext) {
        context.target = HttpTestTarget("localhost", port)
        verificationKey = mintVerificationKey()
    }

    private fun mintVerificationKey(): String {
        val owner = userRepository.save(
            User(email = "pact-${System.nanoTime()}@example.com", name = "Pact Verifier")
        )
        // Creating a scoped key requires holding those scopes, so authorise this one call.
        val previous = SecurityContextHolder.getContext().authentication
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            "pact", null, listOf(SimpleGrantedAuthority(Permission.ADMIN.authority))
        )
        return try {
            apiKeyService.createApiKey(
                CreateApiKeyRequest(
                    ownerId = owner.id,
                    label = "pact verification",
                    ownerType = OwnerType.USER,
                    scopes = Permission.entries.map { it.scope }
                )
            ).plainTextKey
        } finally {
            SecurityContextHolder.getContext().authentication = previous
        }
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider::class)
    fun pactVerificationTestTemplate(context: PactVerificationContext, request: HttpRequest) {
        request.addHeader(ApiKeyAuthFilter.API_KEY_HEADER, verificationKey)
        context.verifyInteraction()
    }

    @State("flows exist")
    fun setupFlowsExist() {
        trailRepository.deleteAll()
        flowRepository.deleteAll()
        val flow = Flow(name = "test-flow", description = "A test flow")
        flow.requiredAttestationTypes = listOf("junit")
        flowRepository.save(flow)
    }

    @State("no state needed")
    fun setupNoState() {
        trailRepository.deleteAll()
        flowRepository.deleteAll()
    }

    @State("a flow with id 7f3f2b99-0000-0000-0000-000000000001 exists")
    fun setupFlowWithKnownId() {
        trailRepository.deleteAll()
        flowRepository.deleteAll()
        val flow = Flow(
            id = UUID.fromString("7f3f2b99-0000-0000-0000-000000000001"),
            name = "compliance-flow",
            description = "A flow for compliance checks"
        )
        flowRepository.save(flow)
    }
}
