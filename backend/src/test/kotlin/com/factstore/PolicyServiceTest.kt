package com.factstore

import com.factstore.application.PolicyService
import com.factstore.dto.CreatePolicyRequest
import com.factstore.dto.UpdatePolicyRequest
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
class PolicyServiceTest {

    @Autowired
    lateinit var policyService: PolicyService

    @Test
    fun `create policy succeeds`() {
        val req = CreatePolicyRequest("prod-policy", enforceProvenance = true, enforceTrailCompliance = true, requiredAttestationTypes = listOf("junit", "snyk"))
        val resp = policyService.createPolicy(req)
        assertEquals("prod-policy", resp.name)
        assertTrue(resp.enforceProvenance)
        assertTrue(resp.enforceTrailCompliance)
        assertEquals(listOf("junit", "snyk"), resp.requiredAttestationTypes)
        assertNotNull(resp.id)
    }

    @Test
    fun `create policy with duplicate name throws ConflictException`() {
        policyService.createPolicy(CreatePolicyRequest("dup-policy"))
        assertThrows<ConflictException> {
            policyService.createPolicy(CreatePolicyRequest("dup-policy"))
        }
    }

    @Test
    fun `get policy by unknown id throws NotFoundException`() {
        assertThrows<NotFoundException> {
            policyService.getPolicy(UUID.randomUUID())
        }
    }

    @Test
    fun `list policies returns all policies`() {
        policyService.createPolicy(CreatePolicyRequest("policy-a"))
        policyService.createPolicy(CreatePolicyRequest("policy-b"))
        val policies = policyService.listPolicies()
        assertTrue(policies.size >= 2)
    }

    @Test
    fun `update policy updates fields`() {
        val created = policyService.createPolicy(CreatePolicyRequest("upd-policy", requiredAttestationTypes = listOf("junit")))
        val updated = policyService.updatePolicy(created.id, UpdatePolicyRequest(enforceProvenance = true, requiredAttestationTypes = listOf("junit", "trivy")))
        assertTrue(updated.enforceProvenance)
        assertEquals(listOf("junit", "trivy"), updated.requiredAttestationTypes)
    }

    @Test
    fun `delete policy removes it`() {
        val created = policyService.createPolicy(CreatePolicyRequest("del-policy"))
        policyService.deletePolicy(created.id)
        assertThrows<NotFoundException> { policyService.getPolicy(created.id) }
    }

    @Test
    fun `delete non-existent policy throws NotFoundException`() {
        assertThrows<NotFoundException> { policyService.deletePolicy(UUID.randomUUID()) }
    }
}
