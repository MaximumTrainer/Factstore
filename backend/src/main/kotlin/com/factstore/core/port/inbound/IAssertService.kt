package com.factstore.core.port.inbound

import com.factstore.dto.AssertRequest
import com.factstore.dto.AssertResponse
import java.util.UUID

interface IAssertService {
    fun assertCompliance(request: AssertRequest): AssertResponse

    /**
     * Asserts a specific pipeline execution. Needs no digest: gates that run before the image
     * is pushed still count toward the trail's verdict.
     */
    fun assertTrail(trailId: UUID, flowId: UUID? = null, sha256Digest: String? = null): AssertResponse
}
