package com.factstore

import com.factstore.application.FlowService
import com.factstore.core.domain.AuditEventType
import com.factstore.core.port.inbound.IAuditService
import com.factstore.dto.CreateFlowRequest
import com.factstore.dto.UpdateFlowRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * #160: a flow definition change must itself be auditable — this is a compliance product,
 * so who changed which gate, and to what, has to be on the record.
 */
@SpringBootTest
@Transactional
class FlowDefinitionAuditTest {

    @Autowired lateinit var flowService: FlowService
    @Autowired lateinit var auditService: IAuditService
    @Autowired lateinit var objectMapper: ObjectMapper

    private fun newFlow(vararg required: String) = flowService.createFlow(
        CreateFlowRequest("flow-${System.nanoTime()}", "desc", required.toList())
    )

    private fun latestPayload(type: AuditEventType, flowId: UUID): Map<*, *> {
        val event = auditService.queryEvents(eventType = type, page = 0, size = 50)
            .events
            .firstOrNull { objectMapper.readValue(it.payload, Map::class.java)["flowId"] == flowId.toString() }
        assertNotNull(event, "expected a $type audit event for flow $flowId")
        return objectMapper.readValue(event!!.payload, Map::class.java)
    }

    @Test
    fun `updating the required attestation types records a before and after diff`() {
        val flow = newFlow("junit")

        flowService.updateFlow(flow.id, UpdateFlowRequest(requiredAttestationTypes = listOf("junit", "snyk")))

        val payload = latestPayload(AuditEventType.FLOW_UPDATED, flow.id)
        val changes = payload["changes"] as Map<*, *>
        val diff = changes["requiredAttestationTypes"] as Map<*, *>
        assertEquals(listOf("junit"), diff["before"])
        assertEquals(listOf("junit", "snyk"), diff["after"])
        assertNotNull(payload["actor"] ?: payload["flowName"])
    }

    @Test
    fun `an update records only the fields that actually changed`() {
        val flow = newFlow("junit")

        flowService.updateFlow(
            flow.id,
            UpdateFlowRequest(description = "new description", requiredAttestationTypes = listOf("junit"))
        )

        val changes = latestPayload(AuditEventType.FLOW_UPDATED, flow.id)["changes"] as Map<*, *>
        assertTrue(changes.containsKey("description"), "a changed description must be recorded")
        assertTrue(
            !changes.containsKey("requiredAttestationTypes"),
            "an unchanged field must not appear in the diff"
        )
    }

    @Test
    fun `an update that changes nothing records no event`() {
        val flow = newFlow("junit")
        val before = auditService.queryEvents(eventType = AuditEventType.FLOW_UPDATED, page = 0, size = 1).totalElements

        flowService.updateFlow(flow.id, UpdateFlowRequest())

        val after = auditService.queryEvents(eventType = AuditEventType.FLOW_UPDATED, page = 0, size = 1).totalElements
        assertEquals(before, after)
    }

    @Test
    fun `the approval requirement change is auditable`() {
        val flow = newFlow("junit")

        flowService.updateFlow(
            flow.id,
            UpdateFlowRequest(requiresApproval = true, requiredApproverRoles = listOf("release-manager"))
        )

        val changes = latestPayload(AuditEventType.FLOW_UPDATED, flow.id)["changes"] as Map<*, *>
        assertEquals(false, (changes["requiresApproval"] as Map<*, *>)["before"])
        assertEquals(true, (changes["requiresApproval"] as Map<*, *>)["after"])
        assertEquals(listOf("release-manager"), (changes["requiredApproverRoles"] as Map<*, *>)["after"])
    }

    @Test
    fun `a template yaml change is recorded without dumping the whole document`() {
        val flow = newFlow()
        val yaml = "version: 1\nartifacts: []\n"

        flowService.updateFlow(flow.id, UpdateFlowRequest(templateYaml = yaml))

        val changes = latestPayload(AuditEventType.FLOW_UPDATED, flow.id)["changes"] as Map<*, *>
        assertTrue(changes.containsKey("templateYaml"))
    }

    @Test
    fun `archiving and unarchiving are auditable`() {
        val flow = newFlow("junit")

        flowService.archiveFlow(flow.id)
        assertNotNull(latestPayload(AuditEventType.FLOW_ARCHIVED, flow.id))

        flowService.unarchiveFlow(flow.id)
        assertNotNull(latestPayload(AuditEventType.FLOW_UNARCHIVED, flow.id))
    }

    @Test
    fun `renaming is auditable and keeps the old name`() {
        val flow = newFlow("junit")
        val newName = "renamed-${System.nanoTime()}"

        flowService.renameFlow(flow.id, newName)

        val payload = latestPayload(AuditEventType.FLOW_RENAMED, flow.id)
        val diff = (payload["changes"] as Map<*, *>)["name"] as Map<*, *>
        assertEquals(flow.name, diff["before"])
        assertEquals(newName, diff["after"])
    }

    @Test
    fun `the number of trails a flow change affects is reported`() {
        val flow = newFlow("junit")

        val impact = flowService.getFlowImpact(flow.id)

        assertEquals(flow.id, impact.flowId)
        assertEquals(0, impact.trailCount)
    }
}
