package com.factstore

import com.factstore.application.EnvironmentService
import com.factstore.core.domain.EnvironmentType
import com.factstore.dto.CreateEnvironmentRequest
import com.factstore.dto.LiveArtifactByRepoResponse
import com.factstore.dto.RecordSnapshotRequest
import com.factstore.dto.SnapshotArtifactRequest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LiveArtifactsByRepoTest {

    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var environmentService: EnvironmentService

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Test
    fun `live-artifacts endpoint groups artifacts by image name across environments`() {
        val suffix = System.nanoTime()
        val env1 = environmentService.createEnvironment(
            CreateEnvironmentRequest("live-env-1-$suffix", EnvironmentType.K8S, "Env 1")
        )
        val env2 = environmentService.createEnvironment(
            CreateEnvironmentRequest("live-env-2-$suffix", EnvironmentType.K8S, "Env 2")
        )

        // Both envs have my-app but with different tags; only env1 has db-service
        environmentService.recordSnapshot(
            env1.id,
            RecordSnapshotRequest(
                recordedBy = "ci-bot",
                artifacts = listOf(
                    SnapshotArtifactRequest("sha256:aaa", "my-app", "v1.0", 2),
                    SnapshotArtifactRequest("sha256:bbb", "db-service", "v2.0", 1)
                )
            )
        )
        environmentService.recordSnapshot(
            env2.id,
            RecordSnapshotRequest(
                recordedBy = "ci-bot",
                artifacts = listOf(
                    SnapshotArtifactRequest("sha256:ccc", "my-app", "v1.1", 3)
                )
            )
        )

        val response = restTemplate.getForEntity(
            "http://localhost:$port/api/v1/environments/live-artifacts",
            Array<LiveArtifactByRepoResponse>::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val results = response.body ?: emptyArray()

        // Should have at least my-app and db-service (there may be others from other tests)
        val myApp = results.find { it.imageName == "my-app" }
        assertNotNull(myApp, "Expected 'my-app' in results")
        val myAppDeployments = myApp!!.deployments
        assertTrue(myAppDeployments.any { it.environmentId == env1.id }, "my-app should be in env1")
        assertTrue(myAppDeployments.any { it.environmentId == env2.id }, "my-app should be in env2")

        val dbService = results.find { it.imageName == "db-service" }
        assertNotNull(dbService, "Expected 'db-service' in results")
        val dbDeployments = dbService!!.deployments
        assertTrue(dbDeployments.any { it.environmentId == env1.id }, "db-service should be in env1")

        // Verify deployment details
        val env1Deploy = myAppDeployments.find { it.environmentId == env1.id }!!
        assertEquals("v1.0", env1Deploy.imageTag)
        assertEquals("sha256:aaa", env1Deploy.sha256Digest)
        assertEquals("live-env-1-$suffix", env1Deploy.environmentName)
    }
}
