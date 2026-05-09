package com.factstore

import com.factstore.application.PolicyService
import com.factstore.dto.CreatePolicyRequest
import com.factstore.dto.UpdatePolicyRequest
import com.factstore.exception.BadRequestException
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

    // Issue #130: Policy versioning

    @Test
    fun `should create version history on policy update`() {
        val created = policyService.createPolicy(CreatePolicyRequest("ver-policy"))
        policyService.updatePolicy(created.id, UpdatePolicyRequest(enforceProvenance = true, changeComment = "Enable provenance"))
        val versions = policyService.listPolicyVersions(created.id)
        assertEquals(1, versions.size)
        assertEquals("Enable provenance", versions[0].changeComment)
    }

    @Test
    fun `should increment version number on each update`() {
        val created = policyService.createPolicy(CreatePolicyRequest("inc-ver-policy"))
        assertEquals(1, created.version)
        val updated = policyService.updatePolicy(created.id, UpdatePolicyRequest(enforceProvenance = true))
        assertEquals(2, updated.version)
        val updated2 = policyService.updatePolicy(created.id, UpdatePolicyRequest(enforceTrailCompliance = true))
        assertEquals(3, updated2.version)
    }

    @Test
    fun `should list all policy versions in descending order`() {
        val created = policyService.createPolicy(CreatePolicyRequest("versions-policy"))
        policyService.updatePolicy(created.id, UpdatePolicyRequest(enforceProvenance = true, changeComment = "first"))
        policyService.updatePolicy(created.id, UpdatePolicyRequest(enforceTrailCompliance = true, changeComment = "second"))
        val versions = policyService.listPolicyVersions(created.id)
        assertEquals(2, versions.size)
        assertTrue(versions[0].version > versions[1].version)
    }

    // Issue #131: Declarative YAML policy schema

    @Test
    fun `should accept valid policyYaml on create`() {
        val yaml = "version: \"1\"\nrules:\n  - type: \"junit\"\n    required: true"
        val resp = policyService.createPolicy(CreatePolicyRequest("yaml-policy", policyYaml = yaml))
        assertEquals(yaml, resp.policyYaml)
    }

    @Test
    fun `should reject invalid YAML on create`() {
        val req = CreatePolicyRequest("bad-yaml-policy", policyYaml = "rules: [unclosed")
        assertThrows<BadRequestException> { policyService.createPolicy(req) }
    }

    @Test
    fun `should reject YAML missing rules on create`() {
        val yaml = "version: \"1\""
        val req = CreatePolicyRequest("no-rules-policy", policyYaml = yaml)
        assertThrows<BadRequestException> { policyService.createPolicy(req) }
    }

    @Test
    fun `should parse and store policy rules from YAML`() {
        val yaml = "version: \"1\"\nrules:\n  - type: \"junit\"\n    required: true\n  - type: \"snyk\"\n    required: true\n    status: \"PASSED\""
        val resp = policyService.createPolicy(CreatePolicyRequest("rules-policy", policyYaml = yaml))
        assertNotNull(resp.policyYaml)
        assertTrue(resp.policyYaml!!.contains("junit"))
    }
}
