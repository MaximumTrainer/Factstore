package com.factstore

import com.factstore.core.domain.TrailStatus
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional

/**
 * Walks the exact API sequence `.github/workflows/dogfood.yml` performs (#150), so the
 * workflow cannot rot silently: a rename or a contract change here fails a test rather than
 * a scheduled pipeline nobody is watching.
 *
 * Every path, field name and expected response shape below mirrors the workflow.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
// Method security is unconditional (#155 FR-1.5), so a test driving a guarded
// endpoint authenticates as a principal holding the scopes rather than switching
// security off.
@WithMockUser(authorities = ["SCOPE_flows:write", "SCOPE_trails:write", "SCOPE_attestations:write", "SCOPE_artifacts:write", "SCOPE_evidence:write", "SCOPE_assert:execute", "SCOPE_policies:write", "SCOPE_admin"])
class DogfoodPipelineTest {

    @Autowired lateinit var mockMvc: MockMvc

    private val gates = listOf("backend-tests", "contract-tests", "frontend-tests", "cli-tests", "build")

    private fun id(json: String): String = Regex("\"id\":\"([^\"]+)\"").find(json)!!.groupValues[1]

    private fun createFlow(name: String): String = mockMvc.post("/api/v1/flows") {
        contentType = MediaType.APPLICATION_JSON
        content = """
            {
              "name": "$name",
              "description": "OpenFactstore gating its own release",
              "requiredAttestationTypes": ${gates.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }}
            }
        """.trimIndent()
    }.andExpect { status { isCreated() } }.andReturn().response.contentAsString

    private fun openTrail(flowId: String, externalId: String) = mockMvc.post("/api/v1/trails") {
        contentType = MediaType.APPLICATION_JSON
        content = """
            {
              "flowId": "$flowId",
              "externalId": "$externalId",
              "gitCommitSha": "deadbeefcafe",
              "gitBranch": "main",
              "gitAuthor": "github-actions",
              "gitAuthorEmail": "actions@users.noreply.github.com"
            }
        """.trimIndent()
    }

    /** Mirrors `.github/scripts/attest.sh`. */
    private fun attest(trailId: String, type: String, status: String) {
        mockMvc.post("/api/v1/trails/$trailId/attestations") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "type": "$type",
                  "name": "$type",
                  "status": "$status",
                  "details": "step outcome: success",
                  "evidenceUrl": "https://github.com/owner/repo/actions/runs/1",
                  "gitCommitSha": "deadbeefcafe",
                  "gitBranch": "main"
                }
            """.trimIndent()
        }.andExpect { status { isCreated() } }
    }

    private fun registerArtifact(trailId: String, digest: String) {
        mockMvc.post("/api/v1/trails/$trailId/artifacts") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "imageName": "openfactstore-backend",
                  "imageTag": "deadbeefcafe",
                  "sha256Digest": "$digest",
                  "reportedBy": "github-actions"
                }
            """.trimIndent()
        }.andExpect { status { isCreated() } }
    }

    @Test
    fun `the dogfood pipeline reaches COMPLIANT when every gate passes`() {
        val flowId = id(createFlow("openfactstore-release-${System.nanoTime()}"))
        val trailId = id(
            openTrail(flowId, "owner/repo@${System.nanoTime()}")
                .andExpect { status { isCreated() } }
                .andReturn().response.contentAsString
        )

        gates.forEach { attest(trailId, it, "PASSED") }
        registerArtifact(trailId, "sha256:${"a".repeat(64)}")

        mockMvc.post("/api/v1/trails/$trailId/assert") {
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("COMPLIANT") }
            jsonPath("$.trailId") { value(trailId) }
        }

        // The workflow's compliance report reads these three back.
        mockMvc.get("/api/v1/trails/$trailId").andExpect {
            status { isOk() }
            jsonPath("$.status") { value(TrailStatus.COMPLIANT.name) }
        }
        mockMvc.get("/api/v1/trails/$trailId/attestations").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(gates.size) }
        }
        mockMvc.get("/api/v1/trails/$trailId/audit").andExpect { status { isOk() } }
    }

    @Test
    fun `a failing gate blocks the release and names the gate that failed`() {
        val flowId = id(createFlow("openfactstore-release-${System.nanoTime()}"))
        val trailId = id(
            openTrail(flowId, "owner/repo@${System.nanoTime()}")
                .andExpect { status { isCreated() } }
                .andReturn().response.contentAsString
        )

        gates.forEach { attest(trailId, it, if (it == "cli-tests") "FAILED" else "PASSED") }

        mockMvc.post("/api/v1/trails/$trailId/assert") {
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("NON_COMPLIANT") }
            jsonPath("$.missingAttestationTypes") { value("cli-tests") }
        }
    }

    @Test
    fun `a gate that never ran is not treated as a pass`() {
        val flowId = id(createFlow("openfactstore-release-${System.nanoTime()}"))
        val trailId = id(
            openTrail(flowId, "owner/repo@${System.nanoTime()}")
                .andExpect { status { isCreated() } }
                .andReturn().response.contentAsString
        )

        // attest.sh records a cancelled or skipped step as PENDING.
        gates.forEach { attest(trailId, it, if (it == "build") "PENDING" else "PASSED") }

        mockMvc.post("/api/v1/trails/$trailId/assert") {
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("NON_COMPLIANT") }
            jsonPath("$.missingAttestationTypes") { value("build") }
        }
    }

    @Test
    fun `re-running the workflow joins the same trail instead of forking the evidence`() {
        val flowId = id(createFlow("openfactstore-release-${System.nanoTime()}"))
        val releaseId = "owner/repo@${System.nanoTime()}"

        val first = openTrail(flowId, releaseId).andExpect { status { isCreated() } }
            .andReturn().response.contentAsString
        // The second call is the re-run: 200, not 201, and the same trail.
        openTrail(flowId, releaseId).andExpect {
            status { isOk() }
            jsonPath("$.id") { value(id(first)) }
        }
    }

    @Test
    fun `another pipeline can find the release trail by its identifier alone`() {
        val flowId = id(createFlow("openfactstore-release-${System.nanoTime()}"))
        val releaseId = "owner/repo@${System.nanoTime()}"
        val trailId = id(
            openTrail(flowId, releaseId).andExpect { status { isCreated() } }
                .andReturn().response.contentAsString
        )

        // The lookup docs/dogfooding.md tells verify-factstore.yml to use.
        mockMvc.get("/api/v1/trails/lookup") {
            param("flowId", flowId)
            param("externalId", releaseId)
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(trailId) }
        }
    }
}
