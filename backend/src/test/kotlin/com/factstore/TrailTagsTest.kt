package com.factstore

import com.factstore.application.FlowService
import com.factstore.application.TrailService
import com.factstore.dto.CreateFlowRequest
import com.factstore.dto.CreateTrailRequest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
class TrailTagsTest {

    @Autowired
    lateinit var flowService: FlowService

    @Autowired
    lateinit var trailService: TrailService

    private fun createFlow() =
        flowService.createFlow(CreateFlowRequest("flow-${System.nanoTime()}", "desc"))

    @Test
    fun `should store and retrieve tags on trail`() {
        val flow = createFlow()
        val tags = mapOf("version" to "1.0", "env" to "ci")
        val resp = trailService.createTrail(
            CreateTrailRequest(
                flowId = flow.id,
                gitCommitSha = "abc123",
                gitBranch = "main",
                gitAuthor = "alice",
                gitAuthorEmail = "alice@example.com",
                tags = tags
            )
        )
        assertEquals(tags, resp.tags)

        val fetched = trailService.getTrail(resp.id)
        assertEquals(tags, fetched.tags)
    }

    @Test
    fun `trail created without tags has empty tags map`() {
        val flow = createFlow()
        val resp = trailService.createTrail(
            CreateTrailRequest(
                flowId = flow.id,
                gitCommitSha = "def456",
                gitBranch = "main",
                gitAuthor = "bob",
                gitAuthorEmail = "bob@example.com"
            )
        )
        assertEquals(emptyMap<String, String>(), resp.tags)
    }
}
