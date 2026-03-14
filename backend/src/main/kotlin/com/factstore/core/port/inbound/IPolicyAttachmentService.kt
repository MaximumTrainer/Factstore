package com.factstore.core.port.inbound

import com.factstore.dto.CreatePolicyAttachmentRequest
import com.factstore.dto.PolicyAttachmentResponse
import java.util.UUID

interface IPolicyAttachmentService {
    fun createAttachment(request: CreatePolicyAttachmentRequest): PolicyAttachmentResponse
    fun listAttachments(): List<PolicyAttachmentResponse>
    fun getAttachment(id: UUID): PolicyAttachmentResponse
    fun deleteAttachment(id: UUID)
}
