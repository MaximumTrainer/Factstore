package com.factstore

import com.factstore.application.AuditEventService
import com.factstore.application.EnvironmentService
import com.factstore.core.domain.AuditEventType
import com.factstore.core.domain.EnvironmentType
import com.factstore.dto.CreateEnvironmentRequest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EnvironmentAuditCsvExportTest {

    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var environmentService: EnvironmentService

    @Autowired
    lateinit var auditEventService: AuditEventService

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Test
    fun `export audit log CSV for environment returns CSV with correct headers`() {
        val env = environmentService.createEnvironment(
            CreateEnvironmentRequest("csv-export-env-${System.nanoTime()}", EnvironmentType.K8S, "Test env")
        )
        auditEventService.record(
            eventType = AuditEventType.ENVIRONMENT_CREATED,
            actor = "ci-bot",
            payload = mapOf("name" to env.name),
            environmentId = env.id
        )
        auditEventService.record(
            eventType = AuditEventType.ARTIFACT_DEPLOYED,
            actor = "deploy-bot",
            payload = mapOf("artifact" to "my-app:v1"),
            environmentId = env.id
        )

        val response = restTemplate.getForEntity(
            "http://localhost:$port/api/v1/environments/${env.id}/audit-log/csv",
            String::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val contentType = response.headers.contentType?.toString() ?: ""
        assertTrue(contentType.contains("text/csv"), "Expected text/csv but got: $contentType")
        val contentDisposition = response.headers["Content-Disposition"]?.firstOrNull() ?: ""
        assertTrue(
            contentDisposition.contains("attachment"),
            "Expected attachment content-disposition but got: $contentDisposition"
        )
        assertTrue(
            contentDisposition.contains("env-${env.id}-audit-log.csv"),
            "Expected filename with env id but got: $contentDisposition"
        )

        val body = response.body ?: ""
        assertTrue(body.contains("timestamp,eventType,actor,details,environmentId,resourceId"), "Missing CSV header row")
        assertTrue(body.contains("ENVIRONMENT_CREATED"), "Missing ENVIRONMENT_CREATED event")
        assertTrue(body.contains("ARTIFACT_DEPLOYED"), "Missing ARTIFACT_DEPLOYED event")
    }

    @Test
    fun `export audit log CSV for non-existent environment returns 404`() {
        val response = restTemplate.getForEntity(
            "http://localhost:$port/api/v1/environments/${UUID.randomUUID()}/audit-log/csv",
            String::class.java
        )
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }
}
