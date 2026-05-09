package com.factstore

import com.factstore.application.ArtifactService
import com.factstore.application.FlowService
import com.factstore.application.SearchService
import com.factstore.application.TrailService
import com.factstore.dto.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@Transactional
class SearchServiceTest {

    @Autowired lateinit var searchService: SearchService
    @Autowired lateinit var flowService: FlowService
    @Autowired lateinit var trailService: TrailService
    @Autowired lateinit var artifactService: ArtifactService

    @Test
    fun `search with blank query returns empty results`() {
        val result = searchService.search("", null)
        assertEquals(0, result.total)
        assertTrue(result.results.isEmpty())
    }

    @Test
    fun `search by git branch finds trail`() {
        val flow = flowService.createFlow(CreateFlowRequest("search-flow-${System.nanoTime()}", "desc"))
        val uniqueBranch = "feature/search-test-${System.nanoTime()}"
        trailService.createTrail(CreateTrailRequest(
            flowId = flow.id,
            gitCommitSha = "abc123",
            gitBranch = uniqueBranch,
            gitAuthor = "tester",
            gitAuthorEmail = "tester@example.com"
        ))

        val result = searchService.search(uniqueBranch, "trail")
        assertTrue(result.total > 0)
        assertTrue(result.results.any { it.type == "trail" && it.metadata["gitBranch"] == uniqueBranch })
    }

    @Test
    fun `search by git author finds trail`() {
        val flow = flowService.createFlow(CreateFlowRequest("search-author-flow-${System.nanoTime()}", "desc"))
        val uniqueAuthor = "unique-author-${System.nanoTime()}"
        trailService.createTrail(CreateTrailRequest(
            flowId = flow.id,
            gitCommitSha = "def456",
            gitBranch = "main",
            gitAuthor = uniqueAuthor,
            gitAuthorEmail = "unique@example.com"
        ))

        val result = searchService.search(uniqueAuthor, null)
        assertTrue(result.total > 0)
        assertTrue(result.results.any { it.type == "trail" && it.metadata["gitAuthor"] == uniqueAuthor })
    }

    @Test
    fun `search by image name finds artifact`() {
        val flow = flowService.createFlow(CreateFlowRequest("search-art-flow-${System.nanoTime()}", "desc"))
        val trail = trailService.createTrail(CreateTrailRequest(
            flowId = flow.id, gitCommitSha = "ghi789", gitBranch = "main",
            gitAuthor = "a", gitAuthorEmail = "a@b.com"
        ))
        val uniqueImage = "myservice-${System.nanoTime()}"
        artifactService.reportArtifact(trail.id, CreateArtifactRequest(
            imageName = uniqueImage, imageTag = "v1.0",
            sha256Digest = "sha256:${System.nanoTime()}", reportedBy = "ci"
        ))

        val result = searchService.search(uniqueImage, "artifact")
        assertTrue(result.total > 0)
        assertTrue(result.results.any { it.type == "artifact" && it.title.startsWith(uniqueImage) })
    }

    @Test
    fun `search with type=trail only returns trail results`() {
        val flow = flowService.createFlow(CreateFlowRequest("search-type-flow-${System.nanoTime()}", "desc"))
        val uniqueAuthor = "trail-only-author-${System.nanoTime()}"
        trailService.createTrail(CreateTrailRequest(
            flowId = flow.id, gitCommitSha = "xyz", gitBranch = "main",
            gitAuthor = uniqueAuthor, gitAuthorEmail = "t@t.com"
        ))

        val result = searchService.search(uniqueAuthor, "trail")
        assertTrue(result.results.all { it.type == "trail" })
    }

    @Test
    fun `search result contains correct metadata`() {
        val flow = flowService.createFlow(CreateFlowRequest("search-meta-flow-${System.nanoTime()}", "desc"))
        val sha = "abcdef1234567890"
        trailService.createTrail(CreateTrailRequest(
            flowId = flow.id, gitCommitSha = sha, gitBranch = "release",
            gitAuthor = "dev", gitAuthorEmail = "dev@corp.com"
        ))

        val result = searchService.search(sha, "trail")
        val found = result.results.find { it.metadata["gitCommitSha"] == sha }
        assertNotNull(found)
        assertEquals("release", found!!.metadata["gitBranch"])
        assertEquals(flow.id.toString(), found.metadata["flowId"])
    }

    @Test
    fun `search with invalid type throws BadRequestException`() {
        val ex = assertThrows(com.factstore.exception.BadRequestException::class.java) {
            searchService.search("anything", "invalid-type")
        }
        assertTrue(ex.message!!.contains("invalid-type"))
    }

    @Test
    fun `search trims leading and trailing whitespace`() {
        val flow = flowService.createFlow(CreateFlowRequest("search-trim-flow-${System.nanoTime()}", "desc"))
        val uniqueBranch = "trim-test-branch-${System.nanoTime()}"
        trailService.createTrail(CreateTrailRequest(
            flowId = flow.id, gitCommitSha = "trim123", gitBranch = uniqueBranch,
            gitAuthor = "trimmer", gitAuthorEmail = "t@t.com"
        ))

        val result = searchService.search("  $uniqueBranch  ", "trail")
        assertTrue(result.total > 0, "Expected to find result for trimmed query")
        assertEquals(uniqueBranch, result.query, "Echoed query should be trimmed")
    }

    // ---- Global search tests (Issue #139) ----

    @Test
    fun `global search returns flows matching query`() {
        val uniqueName = "global-search-flow-${System.nanoTime()}"
        flowService.createFlow(CreateFlowRequest(uniqueName, "description"))

        val result = searchService.search(GlobalSearchRequest(query = uniqueName))
        assertTrue(result.flows.any { it.name == uniqueName }, "Should find flow matching query")
        assertTrue(result.trails.isEmpty() || result.trails.all { true }, "Trails may be empty")
    }

    @Test
    fun `global search filters by org slug`() {
        val orgSlug = "org-${System.nanoTime()}"
        val matchingFlowName = "org-flow-match-${System.nanoTime()}"
        val otherFlowName = "org-flow-other-${System.nanoTime()}"
        flowService.createFlow(CreateFlowRequest(matchingFlowName, "desc", orgSlug = orgSlug))
        flowService.createFlow(CreateFlowRequest(otherFlowName, "desc", orgSlug = "different-org"))

        val result = searchService.search(GlobalSearchRequest(orgSlug = orgSlug))
        assertTrue(result.flows.any { it.name == matchingFlowName }, "Should include flow with matching orgSlug")
        assertTrue(result.flows.none { it.name == otherFlowName }, "Should exclude flow with different orgSlug")
    }

    @Test
    fun `global search filters flows by tag`() {
        val tagKey = "env"
        val tagValue = "production-${System.nanoTime()}"
        val matchingFlowName = "tagged-flow-${System.nanoTime()}"
        val untaggedFlowName = "untagged-flow-${System.nanoTime()}"
        flowService.createFlow(CreateFlowRequest(matchingFlowName, "desc", tags = mapOf(tagKey to tagValue)))
        flowService.createFlow(CreateFlowRequest(untaggedFlowName, "desc"))

        val result = searchService.search(GlobalSearchRequest(tags = mapOf(tagKey to tagValue)))
        assertTrue(result.flows.any { it.name == matchingFlowName }, "Should include flow with matching tag")
        assertTrue(result.flows.none { it.name == untaggedFlowName }, "Should exclude flow without matching tag")
    }

    @Test
    fun `global search with no filters returns all flows trails and artifacts`() {
        val flowName = "all-inclusive-flow-${System.nanoTime()}"
        val flow = flowService.createFlow(CreateFlowRequest(flowName, "desc"))
        val trail = trailService.createTrail(CreateTrailRequest(
            flowId = flow.id, gitCommitSha = "abc123", gitBranch = "main",
            gitAuthor = "dev", gitAuthorEmail = "dev@test.com"
        ))
        val imageName = "all-inclusive-img-${System.nanoTime()}"
        artifactService.reportArtifact(trail.id, CreateArtifactRequest(
            imageName = imageName, imageTag = "v1", sha256Digest = "sha256:${"a".repeat(64)}", reportedBy = "ci"
        ))

        val result = searchService.search(GlobalSearchRequest())
        assertTrue(result.flows.any { it.name == flowName })
        assertTrue(result.trails.any { it.id == trail.id })
        assertTrue(result.artifacts.any { it.imageName == imageName })
    }

    @Test
    fun `global search returns trails matching query`() {
        val flow = flowService.createFlow(CreateFlowRequest("trail-search-flow-${System.nanoTime()}", "desc"))
        val uniqueBranch = "unique-branch-${System.nanoTime()}"
        val trail = trailService.createTrail(CreateTrailRequest(
            flowId = flow.id, gitCommitSha = "sha-abc", gitBranch = uniqueBranch,
            gitAuthor = "dev", gitAuthorEmail = "dev@test.com"
        ))

        val result = searchService.search(GlobalSearchRequest(query = uniqueBranch))
        assertTrue(result.trails.any { it.id == trail.id }, "Should find trail matching git branch query")
    }

    @Test
    fun `global search returns artifacts matching query`() {
        val flow = flowService.createFlow(CreateFlowRequest("artifact-search-flow-${System.nanoTime()}", "desc"))
        val trail = trailService.createTrail(CreateTrailRequest(
            flowId = flow.id, gitCommitSha = "sha-art", gitBranch = "main",
            gitAuthor = "dev", gitAuthorEmail = "dev@test.com"
        ))
        val uniqueImage = "unique-image-${System.nanoTime()}"
        artifactService.reportArtifact(trail.id, CreateArtifactRequest(
            imageName = uniqueImage, imageTag = "latest",
            sha256Digest = "sha256:${"b".repeat(64)}", reportedBy = "ci"
        ))

        val result = searchService.search(GlobalSearchRequest(query = uniqueImage))
        assertTrue(result.artifacts.any { it.imageName == uniqueImage }, "Should find artifact matching image name query")
    }
}
