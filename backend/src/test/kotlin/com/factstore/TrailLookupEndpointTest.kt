package com.factstore

import com.factstore.application.FlowService
import com.factstore.core.port.inbound.ITrailService
import com.factstore.dto.CreateFlowRequest
import com.factstore.dto.CreateTrailRequest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** API surface for #164. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TrailLookupEndpointTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var flowService: FlowService
    @Autowired lateinit var trailService: ITrailService

    private fun newFlowId(): UUID =
        flowService.createFlow(CreateFlowRequest("flow-lk-${System.nanoTime()}", "d", listOf("junit"))).id

    private fun createBody(flowId: UUID, externalId: String) = """
        {
          "flowId": "$flowId",
          "gitCommitSha": "deadbeef",
          "gitBranch": "main",
          "gitAuthor": "a",
          "gitAuthorEmail": "a@example.com",
          "externalId": "$externalId"
        }
    """.trimIndent()

    @Test
    fun `POST trails is idempotent for an external id and reports 200 on the second call`() {
        val flowId = newFlowId()

        val created = mockMvc.post("/api/v1/trails") {
            contentType = MediaType.APPLICATION_JSON
            content = createBody(flowId, "release-abc")
        }.andExpect {
            status { isCreated() }
            jsonPath("$.externalId") { value("release-abc") }
        }.andReturn().response.contentAsString

        val id = Regex("\"id\":\"([^\"]+)\"").find(created)!!.groupValues[1]

        mockMvc.post("/api/v1/trails") {
            contentType = MediaType.APPLICATION_JSON
            content = createBody(flowId, "release-abc")
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(id) }
        }
    }

    @Test
    fun `GET trails lookup resolves by external id`() {
        val flowId = newFlowId()
        val trail = trailService.createTrail(
            CreateTrailRequest(
                flowId = flowId,
                gitCommitSha = "cafebabe",
                gitBranch = "main",
                gitAuthor = "a",
                gitAuthorEmail = "a@example.com",
                externalId = "run-99"
            )
        )

        mockMvc.get("/api/v1/trails/lookup") {
            param("flowId", flowId.toString())
            param("externalId", "run-99")
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(trail.id.toString()) }
            jsonPath("$.externalId") { value("run-99") }
        }
    }

    @Test
    fun `GET trails lookup resolves by commit sha`() {
        val flowId = newFlowId()
        val trail = trailService.createTrail(
            CreateTrailRequest(
                flowId = flowId,
                gitCommitSha = "abcdef123",
                gitBranch = "main",
                gitAuthor = "a",
                gitAuthorEmail = "a@example.com"
            )
        )

        mockMvc.get("/api/v1/trails/lookup") {
            param("flowId", flowId.toString())
            param("gitCommitSha", "abcdef123")
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(trail.id.toString()) }
        }
    }

    @Test
    fun `GET trails lookup returns 404 when nothing matches`() {
        mockMvc.get("/api/v1/trails/lookup") {
            param("flowId", newFlowId().toString())
            param("externalId", "nope")
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `GET trails lookup requires a selector`() {
        mockMvc.get("/api/v1/trails/lookup") {
            param("flowId", newFlowId().toString())
        }.andExpect {
            status { isBadRequest() }
        }
    }
}
