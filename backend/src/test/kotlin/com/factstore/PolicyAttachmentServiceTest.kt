package com.factstore

import com.factstore.application.PolicyAttachmentService
import com.factstore.application.EnvironmentService
import com.factstore.application.PolicyService
import com.factstore.core.domain.EnvironmentType
import com.factstore.dto.CreateEnvironmentRequest
import com.factstore.dto.CreatePolicyAttachmentRequest
import com.factstore.dto.CreatePolicyRequest
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
class PolicyAttachmentServiceTest {

    @Autowired
    lateinit var policyAttachmentService: PolicyAttachmentService

    @Autowired
    lateinit var policyService: PolicyService

    @Autowired
    lateinit var environmentService: EnvironmentService

    private fun createPolicy(name: String) =
        policyService.createPolicy(CreatePolicyRequest(name))

    private fun createEnvironment(name: String) =
        environmentService.createEnvironment(CreateEnvironmentRequest(name, EnvironmentType.K8S, ""))

    @Test
    fun `create attachment succeeds`() {
        val policy = createPolicy("att-policy-1")
        val env = createEnvironment("att-env-1")
        val resp = policyAttachmentService.createAttachment(
            CreatePolicyAttachmentRequest(policy.id, env.id)
        )
        assertEquals(policy.id, resp.policyId)
        assertEquals(env.id, resp.environmentId)
        assertNotNull(resp.id)
    }

    @Test
    fun `create duplicate attachment throws ConflictException`() {
        val policy = createPolicy("att-policy-dup")
        val env = createEnvironment("att-env-dup")
        policyAttachmentService.createAttachment(CreatePolicyAttachmentRequest(policy.id, env.id))
        assertThrows<ConflictException> {
            policyAttachmentService.createAttachment(CreatePolicyAttachmentRequest(policy.id, env.id))
        }
    }

    @Test
    fun `create attachment with unknown policy throws NotFoundException`() {
        val env = createEnvironment("att-env-nopol")
        assertThrows<NotFoundException> {
            policyAttachmentService.createAttachment(
                CreatePolicyAttachmentRequest(UUID.randomUUID(), env.id)
            )
        }
    }

    @Test
    fun `create attachment with unknown environment throws NotFoundException`() {
        val policy = createPolicy("att-policy-noenv")
        assertThrows<NotFoundException> {
            policyAttachmentService.createAttachment(
                CreatePolicyAttachmentRequest(policy.id, UUID.randomUUID())
            )
        }
    }

    @Test
    fun `list attachments returns all attachments`() {
        val policy = createPolicy("att-policy-list")
        val env1 = createEnvironment("att-env-list-1")
        val env2 = createEnvironment("att-env-list-2")
        policyAttachmentService.createAttachment(CreatePolicyAttachmentRequest(policy.id, env1.id))
        policyAttachmentService.createAttachment(CreatePolicyAttachmentRequest(policy.id, env2.id))
        val attachments = policyAttachmentService.listAttachments()
        assertTrue(attachments.size >= 2)
    }

    @Test
    fun `get attachment by unknown id throws NotFoundException`() {
        assertThrows<NotFoundException> {
            policyAttachmentService.getAttachment(UUID.randomUUID())
        }
    }

    @Test
    fun `delete attachment removes it`() {
        val policy = createPolicy("att-policy-del")
        val env = createEnvironment("att-env-del")
        val created = policyAttachmentService.createAttachment(
            CreatePolicyAttachmentRequest(policy.id, env.id)
        )
        policyAttachmentService.deleteAttachment(created.id)
        assertThrows<NotFoundException> { policyAttachmentService.getAttachment(created.id) }
    }

    @Test
    fun `delete non-existent attachment throws NotFoundException`() {
        assertThrows<NotFoundException> { policyAttachmentService.deleteAttachment(UUID.randomUUID()) }
    }
}
