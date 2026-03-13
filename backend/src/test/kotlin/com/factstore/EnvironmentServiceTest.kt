package com.factstore

import com.factstore.application.EnvironmentService
import com.factstore.core.domain.EnvironmentType
import com.factstore.dto.CreateEnvironmentRequest
import com.factstore.dto.UpdateEnvironmentRequest
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
class EnvironmentServiceTest {

    @Autowired
    lateinit var environmentService: EnvironmentService

    @Test
    fun `create environment succeeds`() {
        val req = CreateEnvironmentRequest("prod", EnvironmentType.K8S, "Production cluster")
        val resp = environmentService.createEnvironment(req)
        assertEquals("prod", resp.name)
        assertEquals(EnvironmentType.K8S, resp.type)
        assertEquals("Production cluster", resp.description)
        assertNotNull(resp.id)
    }

    @Test
    fun `create environment with duplicate name throws ConflictException`() {
        environmentService.createEnvironment(CreateEnvironmentRequest("dup-env", EnvironmentType.ECS, ""))
        assertThrows<ConflictException> {
            environmentService.createEnvironment(CreateEnvironmentRequest("dup-env", EnvironmentType.VM, ""))
        }
    }

    @Test
    fun `get environment by unknown id throws NotFoundException`() {
        assertThrows<NotFoundException> {
            environmentService.getEnvironment(UUID.randomUUID())
        }
    }

    @Test
    fun `list environments returns all environments`() {
        environmentService.createEnvironment(CreateEnvironmentRequest("env-a", EnvironmentType.K8S, ""))
        environmentService.createEnvironment(CreateEnvironmentRequest("env-b", EnvironmentType.ECS, ""))
        val envs = environmentService.listEnvironments()
        assertTrue(envs.size >= 2)
    }

    @Test
    fun `update environment updates fields`() {
        val created = environmentService.createEnvironment(CreateEnvironmentRequest("upd-env", EnvironmentType.K8S, "old"))
        val updated = environmentService.updateEnvironment(created.id, UpdateEnvironmentRequest(description = "new", type = EnvironmentType.VM))
        assertEquals("new", updated.description)
        assertEquals(EnvironmentType.VM, updated.type)
    }

    @Test
    fun `delete environment removes it`() {
        val created = environmentService.createEnvironment(CreateEnvironmentRequest("del-env", EnvironmentType.K8S, ""))
        environmentService.deleteEnvironment(created.id)
        assertThrows<NotFoundException> { environmentService.getEnvironment(created.id) }
    }

    @Test
    fun `delete non-existent environment throws NotFoundException`() {
        assertThrows<NotFoundException> { environmentService.deleteEnvironment(UUID.randomUUID()) }
    }
}
