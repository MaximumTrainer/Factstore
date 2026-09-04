package com.factstore

import com.factstore.application.FlowService
import com.factstore.application.HubService
import com.factstore.application.OrgTemplateService
import com.factstore.core.domain.TemplateCategory
import com.factstore.dto.CreateFlowRequest
import com.factstore.dto.CreateOrgTemplateRequest
import com.factstore.dto.UpdateFlowRequest
import com.factstore.exception.BadRequestException
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

/**
 * #162: organisations publish their own templates, a flow records which template it came from,
 * and drift against that template is visible.
 */
@SpringBootTest
@Transactional
class OrgTemplateAndDriftTest {

    @Autowired lateinit var hubService: HubService
    @Autowired lateinit var orgTemplateService: OrgTemplateService
    @Autowired lateinit var flowService: FlowService

    private val houseYaml = """
        version: 1
        trail:
          attestations:
            - name: unit-tests
              type: junit
            - name: house-review
              type: pull-request
    """.trimIndent()

    private fun publish(templateId: String, orgSlug: String? = null, yaml: String = houseYaml) =
        orgTemplateService.create(
            CreateOrgTemplateRequest(
                templateId = templateId,
                name = "House standard",
                description = "The platform team's baseline",
                templateYaml = yaml,
                category = TemplateCategory.SERVICE_TYPE,
                serviceType = "house",
                orgSlug = orgSlug
            )
        )

    // --- Organisation templates -------------------------------------------

    @Test
    fun `an organisation template appears in the catalogue alongside the built-ins`() {
        publish("house-service-${System.nanoTime()}".take(40))

        val ids = hubService.listTemplates().map { it.id }
        assertTrue(ids.contains("service-internal"), "the built-ins are still there")
        assertTrue(ids.any { it.startsWith("house-service-") })
    }

    @Test
    fun `an organisation template can shadow a built-in of the same id`() {
        publish("service-internal", yaml = houseYaml)

        val resolved = hubService.getTemplate("service-internal")

        assertEquals("House standard", resolved.name, "the org template must win")
        assertNotNull(resolved.orgSlug ?: "global")
    }

    @Test
    fun `an organisation only sees its own templates when it asks by slug`() {
        val id = "acme-only-${System.nanoTime()}".take(40)
        publish(id, orgSlug = "acme")

        assertTrue(hubService.listTemplates(orgSlug = "acme").map { it.id }.contains(id))
        assertFalse(hubService.listTemplates(orgSlug = "other-org").map { it.id }.contains(id))
    }

    @Test
    fun `publishing the same template id twice for one organisation is a conflict`() {
        val id = "dup-${System.nanoTime()}".take(40)
        publish(id, orgSlug = "acme")

        assertThrows<ConflictException> { publish(id, orgSlug = "acme") }
    }

    @Test
    fun `an unparseable template is rejected at publication`() {
        assertThrows<BadRequestException> {
            orgTemplateService.create(
                CreateOrgTemplateRequest(
                    templateId = "broken-${System.nanoTime()}".take(40),
                    name = "Broken",
                    templateYaml = "this: [is: not: valid: yaml"
                )
            )
        }
    }

    @Test
    fun `a published template can be updated and withdrawn`() {
        val created = publish("editable-${System.nanoTime()}".take(40))

        val updated = orgTemplateService.update(
            created.id,
            CreateOrgTemplateRequest(
                templateId = created.templateId,
                name = "Renamed",
                description = created.description,
                templateYaml = houseYaml
            )
        )
        assertEquals("Renamed", updated.name)

        orgTemplateService.delete(created.id)
        assertFalse(orgTemplateService.list().any { it.id == created.id })
    }

    @Test
    fun `withdrawing an unknown template is a not-found`() {
        assertThrows<NotFoundException> { orgTemplateService.delete(java.util.UUID.randomUUID()) }
    }

    // --- Applying a template at creation ----------------------------------

    @Test
    fun `creating a flow from a template copies its gates onto the flow`() {
        val flow = flowService.createFlow(
            CreateFlowRequest(
                name = "flow-tmpl-${System.nanoTime()}",
                description = "d",
                requiredAttestationTypes = emptyList(),
                templateId = "service-batch-job"
            )
        )

        assertNotNull(flow.templateYaml, "the template must be copied, not linked")
        assertTrue(flow.templateYaml!!.contains("unit-tests"))
        assertEquals("service-batch-job", flow.templateId)
        assertNotNull(flow.templateVersion)
    }

    @Test
    fun `a flow can be created from a service type and a framework combined`() {
        val flow = flowService.createFlow(
            CreateFlowRequest(
                name = "flow-combo-${System.nanoTime()}",
                description = "d",
                requiredAttestationTypes = emptyList(),
                templateIds = listOf("service-public-api", "slsa-level-2")
            )
        )

        assertTrue(flow.templateYaml!!.contains("api-tests"))
        assertTrue(flow.templateYaml!!.contains("build-provenance"))
        assertEquals("service-public-api+slsa-level-2", flow.templateId)
    }

    @Test
    fun `an explicit templateYaml still wins over a template id`() {
        val explicit = "version: 1\ntrail:\n  attestations:\n    - name: only-this\n      type: custom\n"
        val flow = flowService.createFlow(
            CreateFlowRequest(
                name = "flow-explicit-${System.nanoTime()}",
                description = "d",
                requiredAttestationTypes = emptyList(),
                templateYaml = explicit,
                templateId = "service-batch-job"
            )
        )

        assertTrue(flow.templateYaml!!.contains("only-this"))
    }

    // --- Drift -------------------------------------------------------------

    @Test
    fun `a flow created from a template starts undrifted`() {
        val flow = flowService.createFlow(
            CreateFlowRequest(
                name = "flow-drift-${System.nanoTime()}",
                description = "d",
                requiredAttestationTypes = emptyList(),
                templateId = "service-internal"
            )
        )

        val drift = flowService.getTemplateDrift(flow.id)

        assertFalse(drift.drifted, "expected no drift, got $drift")
        assertEquals("service-internal", drift.templateId)
        assertTrue(drift.missingFromFlow.isEmpty())
        assertTrue(drift.addedToFlow.isEmpty())
    }

    @Test
    fun `removing a gate the template requires shows as drift`() {
        val flow = flowService.createFlow(
            CreateFlowRequest(
                name = "flow-drift2-${System.nanoTime()}",
                description = "d",
                requiredAttestationTypes = emptyList(),
                templateId = "service-internal"
            )
        )

        flowService.updateFlow(
            flow.id,
            UpdateFlowRequest(
                templateYaml = "version: 1\ntrail:\n  attestations:\n    - name: unit-tests\n      type: junit\n"
            )
        )

        val drift = flowService.getTemplateDrift(flow.id)

        assertTrue(drift.drifted)
        assertTrue(drift.missingFromFlow.contains("image-scan"), "expected image-scan missing, got $drift")
    }

    @Test
    fun `adding a local gate shows as drift too`() {
        val flow = flowService.createFlow(
            CreateFlowRequest(
                name = "flow-drift3-${System.nanoTime()}",
                description = "d",
                requiredAttestationTypes = emptyList(),
                templateId = "service-batch-job"
            )
        )

        val extended = hubService.getTemplate("service-batch-job").yaml
            .let { flowService.getFlow(flow.id).templateYaml!! } +
            "    - name: chaos-test\n      type: chaos\n"
        flowService.updateFlow(flow.id, UpdateFlowRequest(templateYaml = extended))

        val drift = flowService.getTemplateDrift(flow.id)

        assertTrue(drift.addedToFlow.contains("chaos-test"), "expected chaos-test added, got $drift")
    }

    @Test
    fun `a flow with no template reports no drift rather than failing`() {
        val flow = flowService.createFlow(
            CreateFlowRequest("flow-notmpl-${System.nanoTime()}", "d", listOf("junit"))
        )

        val drift = flowService.getTemplateDrift(flow.id)

        assertFalse(drift.drifted)
        assertNull(drift.templateId)
    }
}
