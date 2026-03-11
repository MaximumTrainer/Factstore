package com.factstore.core.port.inbound

import com.factstore.dto.ChainOfCustodyResponse

interface IComplianceService {
    fun getChainOfCustody(sha256Digest: String): ChainOfCustodyResponse
}
