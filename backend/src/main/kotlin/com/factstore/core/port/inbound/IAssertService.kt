package com.factstore.core.port.inbound

import com.factstore.dto.AssertRequest
import com.factstore.dto.AssertResponse

interface IAssertService {
    fun assertCompliance(request: AssertRequest): AssertResponse
}
