package com.factstore.core.port.inbound

import com.factstore.dto.CreateDeploymentPolicyRequest
import com.factstore.dto.DeploymentPolicyResponse
import com.factstore.dto.GateEvaluateRequest
import com.factstore.dto.GateEvaluateResponse
import com.factstore.dto.UpdateDeploymentPolicyRequest
import java.util.UUID

interface IDeploymentPolicyService {
    fun createPolicy(request: CreateDeploymentPolicyRequest): DeploymentPolicyResponse
    fun listPolicies(): List<DeploymentPolicyResponse>
    fun getPolicy(id: UUID): DeploymentPolicyResponse
    fun updatePolicy(id: UUID, request: UpdateDeploymentPolicyRequest): DeploymentPolicyResponse
    fun deletePolicy(id: UUID)
    fun evaluateGate(request: GateEvaluateRequest): GateEvaluateResponse
    fun listGateResults(): List<GateEvaluateResponse>
}
