package com.factstore

import com.factstore.application.HubService
import com.factstore.application.template.TemplateParser
import com.factstore.core.domain.TemplateCategory
import com.factstore.dto.ComposeTemplateRequest
import com.factstore.exception.BadRequestException
import com.factstore.exception.NotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

/**
 * #162: the standard set of gates for a service type should be the default, not something
 * each team has to remember, and a service-type template must combine with a regulatory one.
 */
@SpringBootTest
@Transactional
class HubTemplateCatalogueTest {

    @Autowired lateinit var hubService: HubService
    @Autowired lateinit var templateParser: TemplateParser

    @Test
    fun `a starter catalogue of service-type templates ships with the product`() {
        val serviceTypes = hubService.listTemplates(TemplateCategory.SERVICE_TYPE).map { it.id }

        assertTrue(serviceTypes.containsAll(
            listOf("service-public-api", "service-internal", "service-batch-job", "service-frontend")
        ), "expected the starter catalogue, got $serviceTypes")
    }

    @Test
    fun `the existing regulatory templates are still there, categorised as frameworks`() {
        val frameworks = hubService.listTemplates(TemplateCategory.FRAMEWORK).map { it.id }

        assertTrue(frameworks.containsAll(listOf("slsa-level-2", "pci-dss-v4", "sox-itgc")))
        assertFalse(frameworks.contains("service-public-api"))
    }

    @Test
    fun `listing without a category returns everything`() {
        val all = hubService.listTemplates().map { it.id }

        assertTrue(all.contains("service-internal"))
        assertTrue(all.contains("slsa-level-2"))
    }

    @Test
    fun `every service-type template parses and requires at least unit tests and a dependency scan`() {
        hubService.listTemplates(TemplateCategory.SERVICE_TYPE).forEach { template ->
            val parsed = templateParser.parse(template.yaml)
            assertNotNull(parsed, "${template.id} must be a valid flow template")
            val names = parsed!!.trailAttestations.map { it.name }
            assertTrue(names.contains("unit-tests"), "${template.id} should require unit-tests, got $names")
            assertTrue(names.contains("dependency-scan"), "${template.id} should require dependency-scan, got $names")
        }
    }

    @Test
    fun `a public API template requires more than an internal one`() {
        val publicApi = requiredNames("service-public-api")
        val internal = requiredNames("service-internal")

        assertTrue(publicApi.containsAll(listOf("integration-tests", "api-tests")))
        assertFalse(internal.contains("api-tests"))
        assertTrue(publicApi.size > internal.size)
    }

    @Test
    fun `a frontend template requires the checks a frontend actually needs`() {
        val frontend = requiredNames("service-frontend")

        assertTrue(frontend.contains("licence-scan"))
        assertTrue(frontend.contains("accessibility-check"))
    }

    private fun requiredNames(id: String): List<String> =
        templateParser.parse(hubService.getTemplate(id).yaml)!!.trailAttestations.map { it.name }

    // --- Composition ------------------------------------------------------

    @Test
    fun `a service-type template composes with a regulatory framework template`() {
        val result = hubService.compose(
            ComposeTemplateRequest(templateIds = listOf("service-public-api", "slsa-level-2"))
        )

        val names = templateParser.parse(result.templateYaml)!!.trailAttestations.map { it.name }
        assertTrue(names.contains("unit-tests"), "the service-type gates must survive composition")
        assertTrue(names.contains("build-provenance"), "the framework gates must survive composition")
        assertTrue(result.requiredAttestations.containsAll(listOf("unit-tests", "build-provenance")))
    }

    @Test
    fun `composition takes the union, without duplicating a shared gate`() {
        val result = hubService.compose(
            ComposeTemplateRequest(templateIds = listOf("service-public-api", "service-internal"))
        )

        val names = templateParser.parse(result.templateYaml)!!.trailAttestations.map { it.name }
        assertEquals(names.size, names.distinct().size, "a gate required by both must appear once")
        assertTrue(names.contains("unit-tests"))
    }

    @Test
    fun `a name required with two different types is surfaced as a conflict, not silently merged`() {
        val result = hubService.compose(
            ComposeTemplateRequest(templateIds = listOf("service-public-api", "conflicting-test-template"))
        )

        assertTrue(
            result.conflicts.any { it.contains("unit-tests") },
            "expected a conflict on unit-tests, got ${result.conflicts}"
        )
    }

    @Test
    fun `composing an unknown template is a not-found`() {
        assertThrows<NotFoundException> {
            hubService.compose(ComposeTemplateRequest(templateIds = listOf("no-such-template")))
        }
    }

    @Test
    fun `composing needs at least one template`() {
        assertThrows<BadRequestException> {
            hubService.compose(ComposeTemplateRequest(templateIds = emptyList()))
        }
    }

    @Test
    fun `composing one template is just that template`() {
        val result = hubService.compose(ComposeTemplateRequest(templateIds = listOf("service-batch-job")))

        assertEquals(requiredNames("service-batch-job").toSet(), result.requiredAttestations.toSet())
        assertTrue(result.conflicts.isEmpty())
    }
}
