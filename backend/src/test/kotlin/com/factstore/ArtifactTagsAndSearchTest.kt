package com.factstore

import com.factstore.application.ArtifactService
import com.factstore.application.FlowService
import com.factstore.application.TrailService
import com.factstore.dto.CreateArtifactRequest
import com.factstore.dto.CreateFlowRequest
import com.factstore.dto.CreateTrailRequest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
class ArtifactTagsAndSearchTest {

    @Autowired
    lateinit var flowService: FlowService

    @Autowired
    lateinit var trailService: TrailService

    @Autowired
    lateinit var artifactService: ArtifactService

    private fun createFlow() =
        flowService.createFlow(CreateFlowRequest("flow-${System.nanoTime()}", "desc"))

    private fun createTrail(flowId: java.util.UUID, sha: String = "abc123") =
        trailService.createTrail(
            CreateTrailRequest(
                flowId = flowId,
                gitCommitSha = sha,
                gitBranch = "main",
                gitAuthor = "alice",
                gitAuthorEmail = "alice@example.com"
            )
        )

    private fun uniqueDigest() = "sha256:${"a".repeat(64 - System.nanoTime().toString().length)}${System.nanoTime()}"

    @Test
    fun `should store and retrieve tags on artifact`() {
        val flow = createFlow()
        val trail = createTrail(flow.id)
        val tags = mapOf("image-type" to "app", "team" to "platform")
        val resp = artifactService.reportArtifact(
            trail.id,
            CreateArtifactRequest(
                imageName = "my-app",
                imageTag = "v1.0.0",
                sha256Digest = uniqueDigest(),
                reportedBy = "ci-bot",
                tags = tags
            )
        )
        assertEquals(tags, resp.tags)

        val listed = artifactService.listArtifactsForTrail(trail.id)
        assertEquals(1, listed.size)
        assertEquals(tags, listed[0].tags)
    }

    @Test
    fun `artifact created without tags has empty tags map`() {
        val flow = createFlow()
        val trail = createTrail(flow.id)
        val resp = artifactService.reportArtifact(
            trail.id,
            CreateArtifactRequest("my-app", "v1.0.0", uniqueDigest(), reportedBy = "ci-bot")
        )
        assertEquals(emptyMap<String, String>(), resp.tags)
    }

    @Test
    fun `should find artifacts by git commit SHA`() {
        val flow = createFlow()
        val commitSha = "deadbeef${System.nanoTime()}"
        val trail = createTrail(flow.id, sha = commitSha)
        val digest = uniqueDigest()
        artifactService.reportArtifact(
            trail.id,
            CreateArtifactRequest("my-app", "v1.0.0", digest, reportedBy = "ci-bot")
        )

        val results = artifactService.searchByCommitSha(commitSha)

        assertEquals(1, results.size)
        assertEquals(digest, results[0].sha256Digest)
        assertEquals(trail.id, results[0].trailId)
    }

    @Test
    fun `searchByCommitSha returns empty list when no trails match`() {
        val results = artifactService.searchByCommitSha("nonexistent-sha-xyz-${System.nanoTime()}")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `searchByCommitSha returns all artifacts from all trails with that SHA`() {
        val flow = createFlow()
        val commitSha = "shared-sha-${System.nanoTime()}"
        val trail1 = createTrail(flow.id, sha = commitSha)
        val trail2 = createTrail(flow.id, sha = commitSha)
        val digest1 = uniqueDigest()
        val digest2 = uniqueDigest()
        artifactService.reportArtifact(
            trail1.id,
            CreateArtifactRequest("app1", "v1", digest1, reportedBy = "bot")
        )
        artifactService.reportArtifact(
            trail2.id,
            CreateArtifactRequest("app2", "v1", digest2, reportedBy = "bot")
        )

        val results = artifactService.searchByCommitSha(commitSha)

        assertEquals(2, results.size)
        assertTrue(results.map { it.sha256Digest }.containsAll(listOf(digest1, digest2)))
    }
}
