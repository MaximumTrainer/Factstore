package com.factstore

import com.factstore.application.LogicalEnvironmentService
import com.factstore.dto.CreateLogicalEnvironmentRequest
import com.factstore.dto.UpdateLogicalEnvironmentRequest
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
class LogicalEnvironmentServiceTest {

    @Autowired
    lateinit var logicalEnvironmentService: LogicalEnvironmentService

    @Test
    fun `create logical environment succeeds`() {
        val req = CreateLogicalEnvironmentRequest("prod-group", "All production envs")
        val resp = logicalEnvironmentService.createLogicalEnvironment(req)
        assertEquals("prod-group", resp.name)
        assertEquals("All production envs", resp.description)
        assertNotNull(resp.id)
    }

    @Test
    fun `create logical environment with duplicate name throws ConflictException`() {
        logicalEnvironmentService.createLogicalEnvironment(CreateLogicalEnvironmentRequest("dup-le"))
        assertThrows<ConflictException> {
            logicalEnvironmentService.createLogicalEnvironment(CreateLogicalEnvironmentRequest("dup-le"))
        }
    }

    @Test
    fun `get logical environment by unknown id throws NotFoundException`() {
        assertThrows<NotFoundException> {
            logicalEnvironmentService.getLogicalEnvironment(UUID.randomUUID())
        }
    }

    @Test
    fun `list logical environments returns all`() {
        logicalEnvironmentService.createLogicalEnvironment(CreateLogicalEnvironmentRequest("le-a"))
        logicalEnvironmentService.createLogicalEnvironment(CreateLogicalEnvironmentRequest("le-b"))
        val envs = logicalEnvironmentService.listLogicalEnvironments()
        assertTrue(envs.size >= 2)
    }

    @Test
    fun `update logical environment updates fields`() {
        val created = logicalEnvironmentService.createLogicalEnvironment(
            CreateLogicalEnvironmentRequest("le-upd", "old desc")
        )
        val updated = logicalEnvironmentService.updateLogicalEnvironment(
            created.id, UpdateLogicalEnvironmentRequest(description = "new desc")
        )
        assertEquals("new desc", updated.description)
        assertEquals("le-upd", updated.name)
    }

    @Test
    fun `update logical environment with duplicate name throws ConflictException`() {
        logicalEnvironmentService.createLogicalEnvironment(CreateLogicalEnvironmentRequest("le-existing"))
        val second = logicalEnvironmentService.createLogicalEnvironment(CreateLogicalEnvironmentRequest("le-second"))
        assertThrows<ConflictException> {
            logicalEnvironmentService.updateLogicalEnvironment(
                second.id, UpdateLogicalEnvironmentRequest(name = "le-existing")
            )
        }
    }

    @Test
    fun `delete logical environment removes it`() {
        val created = logicalEnvironmentService.createLogicalEnvironment(CreateLogicalEnvironmentRequest("le-del"))
        logicalEnvironmentService.deleteLogicalEnvironment(created.id)
        assertThrows<NotFoundException> { logicalEnvironmentService.getLogicalEnvironment(created.id) }
    }

    @Test
    fun `delete non-existent logical environment throws NotFoundException`() {
        assertThrows<NotFoundException> { logicalEnvironmentService.deleteLogicalEnvironment(UUID.randomUUID()) }
    }
}
