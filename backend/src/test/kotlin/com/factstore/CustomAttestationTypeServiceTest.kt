package com.factstore

import com.factstore.core.port.inbound.ICustomAttestationTypeService
import com.factstore.dto.CreateCustomAttestationTypeRequest
import com.factstore.dto.UpdateCustomAttestationTypeRequest
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
class CustomAttestationTypeServiceTest {
    @Autowired
    lateinit var service: ICustomAttestationTypeService

    @Test
    fun `should create and retrieve custom attestation type`() {
        val response = service.createType(CreateCustomAttestationTypeRequest("snyk-scan", "Snyk security scan"))
        assertNotNull(response.id)
        assertEquals("snyk-scan", response.name)
        assertEquals(1, response.version)

        val fetched = service.getType(response.id)
        assertEquals("snyk-scan", fetched.name)
    }

    @Test
    fun `should list only active types by default`() {
        service.createType(CreateCustomAttestationTypeRequest("type-a", "A"))
        val typeB = service.createType(CreateCustomAttestationTypeRequest("type-b", "B"))
        service.archiveType(typeB.id)

        val active = service.listTypes()
        assertEquals(1, active.size)
        assertEquals("type-a", active[0].name)

        val all = service.listTypes(includeArchived = true)
        assertEquals(2, all.size)
    }

    @Test
    fun `should update description and increment version`() {
        val created = service.createType(CreateCustomAttestationTypeRequest("updatable", "original"))
        val updated = service.updateType(created.id, UpdateCustomAttestationTypeRequest(description = "updated"))
        assertEquals("updated", updated.description)
        assertEquals(2, updated.version)
    }

    @Test
    fun `should archive and unarchive type`() {
        val created = service.createType(CreateCustomAttestationTypeRequest("archivable", "desc"))
        val archived = service.archiveType(created.id)
        assertNotNull(archived.archivedAt)

        val unarchived = service.unarchiveType(created.id)
        assertNull(unarchived.archivedAt)
    }

    @Test
    fun `should throw ConflictException for duplicate name`() {
        service.createType(CreateCustomAttestationTypeRequest("duplicate", "desc"))
        assertThrows<ConflictException> {
            service.createType(CreateCustomAttestationTypeRequest("duplicate", "other"))
        }
    }

    @Test
    fun `should throw NotFoundException for unknown id`() {
        assertThrows<NotFoundException> {
            service.getType(java.util.UUID.randomUUID())
        }
    }
}
