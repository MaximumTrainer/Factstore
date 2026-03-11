package com.factstore.core.port.inbound

import com.factstore.core.domain.EvidenceFile
import com.factstore.dto.EvidenceFileResponse
import java.util.UUID

interface IEvidenceVaultService {
    fun store(attestationId: UUID, fileName: String, contentType: String, content: ByteArray): EvidenceFile
    fun findByAttestationId(attestationId: UUID): List<EvidenceFile>
    fun verifyIntegrity(id: UUID): Boolean
}
