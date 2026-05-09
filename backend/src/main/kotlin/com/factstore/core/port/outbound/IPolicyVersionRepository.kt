package com.factstore.core.port.outbound

import com.factstore.core.domain.PolicyVersion
import java.util.UUID

interface IPolicyVersionRepository {
    fun save(version: PolicyVersion): PolicyVersion
    fun findAllByPolicyId(policyId: UUID): List<PolicyVersion>
}
