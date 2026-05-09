package com.factstore.core

import com.factstore.adapter.mock.InMemoryFlowRepository
import com.factstore.application.FlowService
import com.factstore.dto.CreateFlowRequest
import com.factstore.dto.UpdateFlowRequest
import com.factstore.exception.BadRequestException
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

/**
 * Unit test for FlowService that runs without a Spring context.
 * Uses InMemoryFlowRepository (a mock adapter) to demonstrate that the
 * application logic can be tested in complete isolation from infrastructure.
 */
class FlowServiceUnitTest {

    private lateinit var flowService: FlowService

    @BeforeEach
    fun setUp() {
        flowService = FlowService(InMemoryFlowRepository())
    }

    @Test
    fun `create flow succeeds and returns response with generated id`() {
        val req = CreateFlowRequest("unit-flow", "unit desc", listOf("junit", "snyk"))
        val resp = flowService.createFlow(req)
        assertEquals("unit-flow", resp.name)
        assertEquals("unit desc", resp.description)
        assertEquals(listOf("junit", "snyk"), resp.requiredAttestationTypes)
        assertNotNull(resp.id)
    }

    @Test
    fun `create flow with duplicate name throws ConflictException`() {
        flowService.createFlow(CreateFlowRequest("dup-flow", "first"))
        assertThrows<ConflictException> {
            flowService.createFlow(CreateFlowRequest("dup-flow", "second"))
        }
    }

    @Test
    fun `get flow by unknown id throws NotFoundException`() {
        assertThrows<NotFoundException> {
            flowService.getFlow(UUID.randomUUID())
        }
    }

    @Test
    fun `list flows returns all created flows`() {
        flowService.createFlow(CreateFlowRequest("flow-a", "a"))
        flowService.createFlow(CreateFlowRequest("flow-b", "b"))
        val flows = flowService.listFlows()
        assertEquals(2, flows.size)
        assertTrue(flows.map { it.name }.containsAll(listOf("flow-a", "flow-b")))
    }

    @Test
    fun `update flow changes the specified fields`() {
        val created = flowService.createFlow(CreateFlowRequest("upd-flow", "old desc", listOf("junit")))
        val updated = flowService.updateFlow(
            created.id,
            UpdateFlowRequest(description = "new desc", requiredAttestationTypes = listOf("junit", "trivy"))
        )
        assertEquals("new desc", updated.description)
        assertEquals(listOf("junit", "trivy"), updated.requiredAttestationTypes)
    }

    @Test
    fun `update flow name to duplicate name throws ConflictException`() {
        flowService.createFlow(CreateFlowRequest("existing-flow", "desc"))
        val other = flowService.createFlow(CreateFlowRequest("other-flow", "desc"))
        assertThrows<ConflictException> {
            flowService.updateFlow(other.id, UpdateFlowRequest(name = "existing-flow"))
        }
    }

    @Test
    fun `delete flow removes it from storage`() {
        val created = flowService.createFlow(CreateFlowRequest("del-flow", "desc"))
        flowService.deleteFlow(created.id)
        assertThrows<NotFoundException> { flowService.getFlow(created.id) }
    }

    @Test
    fun `delete non-existent flow throws NotFoundException`() {
        assertThrows<NotFoundException> { flowService.deleteFlow(UUID.randomUUID()) }
    }

    @Test
    fun `getFlowEntity returns the domain entity directly`() {
        val created = flowService.createFlow(CreateFlowRequest("entity-flow", "desc", listOf("trivy")))
        val entity = flowService.getFlowEntity(created.id)
        assertEquals(created.id, entity.id)
        assertEquals(listOf("trivy"), entity.requiredAttestationTypes)
    }

    @Test
    fun `create flow with tags stores and returns them`() {
        val tags = mapOf("env" to "prod", "team" to "platform")
        val resp = flowService.createFlow(CreateFlowRequest("tagged-flow", "desc", tags = tags))
        assertEquals(tags, resp.tags)
    }

    @Test
    fun `update flow tags replaces existing tags`() {
        val created = flowService.createFlow(CreateFlowRequest("tag-upd-flow", "desc", tags = mapOf("old" to "value")))
        val updated = flowService.updateFlow(created.id, UpdateFlowRequest(tags = mapOf("new" to "value2")))
        assertEquals(mapOf("new" to "value2"), updated.tags)
    }

    @Test
    fun `create flow with too many tags throws IllegalArgumentException`() {
        val tags = (1..51).associate { "k$it" to "v$it" }
        assertThrows<BadRequestException> {
            flowService.createFlow(CreateFlowRequest("many-tags-flow", "desc", tags = tags))
        }
    }

    @Test
    fun `create flow with tag key exceeding 64 chars throws IllegalArgumentException`() {
        val longKey = "k".repeat(65)
        assertThrows<BadRequestException> {
            flowService.createFlow(CreateFlowRequest("long-key-flow", "desc", tags = mapOf(longKey to "value")))
        }
    }

    @Test
    fun `create flow with tag value exceeding 256 chars throws IllegalArgumentException`() {
        val longValue = "v".repeat(257)
        assertThrows<BadRequestException> {
            flowService.createFlow(CreateFlowRequest("long-val-flow", "desc", tags = mapOf("key" to longValue)))
        }
    }

    // ── Issue #136: Archive / Unarchive ──────────────────────────────────────

    @Test
    fun `archive flow hides it from list`() {
        val created = flowService.createFlow(CreateFlowRequest("arc-flow", "desc"))
        flowService.archiveFlow(created.id)
        val flows = flowService.listFlows()
        assertTrue(flows.none { it.id == created.id })
    }

    @Test
    fun `archived flow can be unarchived`() {
        val created = flowService.createFlow(CreateFlowRequest("unarch-flow", "desc"))
        flowService.archiveFlow(created.id)
        val unarchived = flowService.unarchiveFlow(created.id)
        assertNull(unarchived.archivedAt)
        val flows = flowService.listFlows()
        assertTrue(flows.any { it.id == created.id })
    }

    @Test
    fun `archiving non-existent flow throws NotFoundException`() {
        assertThrows<NotFoundException> { flowService.archiveFlow(UUID.randomUUID()) }
    }

    @Test
    fun `creating flow with name of archived flow succeeds`() {
        val first = flowService.createFlow(CreateFlowRequest("recycled-name", "original"))
        flowService.archiveFlow(first.id)
        val second = flowService.createFlow(CreateFlowRequest("recycled-name", "new active"))
        assertNotNull(second.id)
        assertNotEquals(first.id, second.id)
    }

    // ── Issue #137: Rename with old-name forwarding ──────────────────────────

    @Test
    fun `rename flow updates name and records previous name`() {
        val created = flowService.createFlow(CreateFlowRequest("original-name", "desc"))
        val renamed = flowService.renameFlow(created.id, "new-name")
        assertEquals("new-name", renamed.name)
        val entity = flowService.getFlowEntity(created.id)
        assertTrue(entity.parsedPreviousNames.contains("original-name"))
    }

    @Test
    fun `rename to conflicting active name throws ConflictException`() {
        flowService.createFlow(CreateFlowRequest("taken-name", "desc"))
        val other = flowService.createFlow(CreateFlowRequest("to-rename", "desc"))
        assertThrows<ConflictException> {
            flowService.renameFlow(other.id, "taken-name")
        }
    }

    @Test
    fun `renamed flow can be found by old name via repository`() {
        val repo = InMemoryFlowRepository()
        val svc = FlowService(repo)
        val created = svc.createFlow(CreateFlowRequest("old-flow-name", "desc"))
        svc.renameFlow(created.id, "new-flow-name")
        val found = repo.findByNameOrPreviousName("old-flow-name")
        assertNotNull(found)
        assertEquals(created.id, found!!.id)
    }
}
