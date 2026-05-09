package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.PolicyVersion
import com.factstore.core.port.outbound.IPolicyVersionRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface PolicyVersionRepositoryJpa : JpaRepository<PolicyVersion, UUID> {
    fun findAllByPolicyIdOrderByVersionDesc(policyId: UUID): List<PolicyVersion>
}

@Component
class PolicyVersionRepositoryAdapter(private val jpa: PolicyVersionRepositoryJpa) : IPolicyVersionRepository {
    override fun save(v: PolicyVersion): PolicyVersion = jpa.save(v)
    override fun findAllByPolicyId(policyId: UUID): List<PolicyVersion> =
        jpa.findAllByPolicyIdOrderByVersionDesc(policyId)
}
