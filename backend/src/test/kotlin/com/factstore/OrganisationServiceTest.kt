package com.factstore

import com.factstore.application.OrganisationService
import com.factstore.dto.CreateOrganisationRequest
import com.factstore.dto.UpdateOrganisationRequest
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@Transactional
class OrganisationServiceTest {

    @Autowired
    lateinit var organisationService: OrganisationService

    @Test
    fun `create organisation succeeds`() {
        val req = CreateOrganisationRequest("my-org", "My Organisation", "An org for testing")
        val resp = organisationService.createOrganisation(req)
        assertEquals("my-org", resp.slug)
        assertEquals("My Organisation", resp.name)
        assertEquals("An org for testing", resp.description)
        assertNotNull(resp.id)
    }

    @Test
    fun `create organisation with duplicate slug throws ConflictException`() {
        organisationService.createOrganisation(CreateOrganisationRequest("dup-org", "Dup Org"))
        assertThrows<ConflictException> {
            organisationService.createOrganisation(CreateOrganisationRequest("dup-org", "Another Org"))
        }
    }

    @Test
    fun `get organisation by unknown id throws NotFoundException`() {
        assertThrows<NotFoundException> {
            organisationService.getOrganisation(UUID.randomUUID())
        }
    }

    @Test
    fun `list organisations returns all organisations`() {
        organisationService.createOrganisation(CreateOrganisationRequest("org-a", "Org A"))
        organisationService.createOrganisation(CreateOrganisationRequest("org-b", "Org B"))
        val orgs = organisationService.listOrganisations()
        assertTrue(orgs.size >= 2)
    }

    @Test
    fun `update organisation updates fields`() {
        val created = organisationService.createOrganisation(CreateOrganisationRequest("upd-org", "Old Name"))
        val updated = organisationService.updateOrganisation(created.id, UpdateOrganisationRequest(name = "New Name", description = "updated"))
        assertEquals("New Name", updated.name)
        assertEquals("updated", updated.description)
    }

    @Test
    fun `delete organisation removes it`() {
        val created = organisationService.createOrganisation(CreateOrganisationRequest("del-org", "Del Org"))
        organisationService.deleteOrganisation(created.id)
        assertThrows<NotFoundException> { organisationService.getOrganisation(created.id) }
    }

    @Test
    fun `delete non-existent organisation throws NotFoundException`() {
        assertThrows<NotFoundException> { organisationService.deleteOrganisation(UUID.randomUUID()) }
    }
}
