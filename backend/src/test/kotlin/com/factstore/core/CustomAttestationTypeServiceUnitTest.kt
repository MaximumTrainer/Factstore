package com.factstore.core

import com.factstore.adapter.mock.InMemoryCustomAttestationTypeRepository
import com.factstore.application.CustomAttestationTypeService
import com.factstore.dto.CreateCustomAttestationTypeRequest
import com.factstore.dto.UpdateCustomAttestationTypeRequest
import com.factstore.exception.BadRequestException
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CustomAttestationTypeServiceUnitTest {
    private lateinit var repo: InMemoryCustomAttestationTypeRepository
    private lateinit var service: CustomAttestationTypeService

    @BeforeEach
    fun setup() {
        repo = InMemoryCustomAttestationTypeRepository()
        service = CustomAttestationTypeService(repo, ObjectMapper())
    }

    @Test
    fun `should create a custom attestation type`() {
        val response = service.createType(CreateCustomAttestationTypeRequest(name = "junit-results", description = "JUnit test results"))
        assertNotNull(response.id)
        assertEquals("junit-results", response.name)
        assertEquals(1, response.version)
        assertNull(response.archivedAt)
    }

    @Test
    fun `should list only active types`() {
        service.createType(CreateCustomAttestationTypeRequest("active-type", "Active"))
        val archived = service.createType(CreateCustomAttestationTypeRequest("archived-type", "Archived"))
        service.archiveType(archived.id)
        val list = service.listTypes()
        assertEquals(1, list.size)
        assertEquals("active-type", list[0].name)
    }

    @Test
    fun `should get type by id`() {
        val created = service.createType(CreateCustomAttestationTypeRequest("my-type", "desc"))
        val fetched = service.getType(created.id)
        assertEquals(created.id, fetched.id)
    }

    @Test
    fun `should throw NotFoundException for unknown id`() {
        assertThrows<NotFoundException> {
            service.getType(java.util.UUID.randomUUID())
        }
    }

    @Test
    fun `should update description and increment version`() {
        val created = service.createType(CreateCustomAttestationTypeRequest("my-type", "original"))
        val updated = service.updateType(created.id, UpdateCustomAttestationTypeRequest(description = "updated"))
        assertEquals("updated", updated.description)
        assertEquals(2, updated.version)
    }

    @Test
    fun `should archive and unarchive type`() {
        val created = service.createType(CreateCustomAttestationTypeRequest("my-type", "desc"))
        val archived = service.archiveType(created.id)
        assertNotNull(archived.archivedAt)
        val unarchived = service.unarchiveType(created.id)
        assertNull(unarchived.archivedAt)
    }

    @Test
    fun `should throw ConflictException when creating duplicate name`() {
        service.createType(CreateCustomAttestationTypeRequest("dup", "desc"))
        assertThrows<ConflictException> {
            service.createType(CreateCustomAttestationTypeRequest("dup", "other"))
        }
    }

    @Test
    fun `should store and return schemaJson`() {
        val schema = """{"type":"object","properties":{"passed":{"type":"boolean"}}}"""
        val created = service.createType(CreateCustomAttestationTypeRequest("schema-type", "desc", schemaJson = schema))
        assertEquals(schema, created.schemaJson)

        val fetched = service.getType(created.id)
        assertEquals(schema, fetched.schemaJson)
    }

    @Test
    fun `should reject invalid JSON as schemaJson`() {
        assertThrows<BadRequestException> {
            service.createType(CreateCustomAttestationTypeRequest("bad-schema-type", "desc", schemaJson = "not-valid-json{"))
        }
    }

    @Test
    fun `should store and return jqExpression`() {
        val created = service.createType(CreateCustomAttestationTypeRequest("jq-type", "desc", jqExpression = ".passed == true"))
        assertEquals(".passed == true", created.jqExpression)
    }

    @Test
    fun `should update schemaJson and jqExpression`() {
        val created = service.createType(CreateCustomAttestationTypeRequest("updatable", "desc"))
        val schema = """{"type":"object"}"""
        val updated = service.updateType(created.id, UpdateCustomAttestationTypeRequest(schemaJson = schema, jqExpression = ".ok"))
        assertEquals(schema, updated.schemaJson)
        assertEquals(".ok", updated.jqExpression)
    }
}
