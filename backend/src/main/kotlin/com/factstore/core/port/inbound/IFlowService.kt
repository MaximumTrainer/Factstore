package com.factstore.core.port.inbound

import com.factstore.core.domain.Flow
import com.factstore.dto.CreateFlowRequest
import com.factstore.dto.FlowResponse
import com.factstore.dto.FlowTemplateResponse
import com.factstore.dto.PageResponse
import com.factstore.dto.UpdateFlowRequest
import java.util.UUID

interface IFlowService {
    fun createFlow(request: CreateFlowRequest): FlowResponse
    fun listFlows(includeArchived: Boolean = false): List<FlowResponse>
    fun listFlows(page: Int, size: Int): PageResponse<FlowResponse>
    fun getFlow(id: UUID): FlowResponse
    fun updateFlow(id: UUID, request: UpdateFlowRequest): FlowResponse
    fun deleteFlow(id: UUID)
    fun getFlowEntity(id: UUID): Flow
    fun listFlowsByOrg(orgSlug: String): List<FlowResponse>
    fun getFlowTemplate(id: UUID): FlowTemplateResponse
    fun archiveFlow(id: UUID): FlowResponse
    fun unarchiveFlow(id: UUID): FlowResponse
    fun renameFlow(id: UUID, newName: String): FlowResponse
}
