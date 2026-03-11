# Factstore API Reference

Complete REST API reference for the Factstore Supply Chain Compliance Fact Store.

**Base URL:** `http://localhost:8080/api/v1`

**Interactive documentation:** When the backend is running, Swagger UI is available at [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html).

---

## Table of Contents

- [Flows](#flows)
- [Trails](#trails)
- [Artifacts](#artifacts)
- [Attestations](#attestations)
- [Evidence Files](#evidence-files)
- [Compliance Assertion](#compliance-assertion)
- [Chain of Custody](#chain-of-custody)
- [Error Responses](#error-responses)

---

## Flows

Manage compliance flows — named policies that define which attestation types are required.

### Create a Flow

```
POST /api/v1/flows
```

**Request Body:**

| Field | Type | Required | Description |
|---|---|---|---|
| `name` | string | Yes | Unique flow name. |
| `description` | string | No | Flow description. Defaults to `""`. |
| `requiredAttestationTypes` | string[] | No | List of required attestation type names (e.g., `["junit", "trivy"]`). Defaults to `[]`. |

**Example:**

```bash
curl -X POST http://localhost:8080/api/v1/flows \
  -H "Content-Type: application/json" \
  -d '{
    "name": "production-deploy",
    "description": "Production deployment requirements",
    "requiredAttestationTypes": ["junit", "trivy"]
  }'
```

**Response:** `200 OK`

```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "name": "production-deploy",
  "description": "Production deployment requirements",
  "requiredAttestationTypes": ["junit", "trivy"],
  "createdAt": "2026-03-11T10:00:00Z",
  "updatedAt": "2026-03-11T10:00:00Z"
}
```

**Errors:**
- `409 Conflict` — A flow with that name already exists.

---

### List All Flows

```
GET /api/v1/flows
```

**Response:** `200 OK` — Array of `FlowResponse`.

---

### Get a Flow

```
GET /api/v1/flows/{id}
```

| Parameter | Type | Description |
|---|---|---|
| `id` | UUID (path) | Flow ID. |

**Response:** `200 OK` — `FlowResponse`.

**Errors:**
- `404 Not Found` — Flow does not exist.

---

### Update a Flow

```
PUT /api/v1/flows/{id}
```

| Parameter | Type | Description |
|---|---|---|
| `id` | UUID (path) | Flow ID. |

**Request Body:**

| Field | Type | Required | Description |
|---|---|---|---|
| `name` | string | No | Updated name. |
| `description` | string | No | Updated description. |
| `requiredAttestationTypes` | string[] | No | Updated list of required attestation types. |

Only provided fields are updated; omitted fields retain their current values.

**Response:** `200 OK` — Updated `FlowResponse`.

**Errors:**
- `404 Not Found` — Flow does not exist.
- `409 Conflict` — Updated name conflicts with an existing flow.

---

### Delete a Flow

```
DELETE /api/v1/flows/{id}
```

| Parameter | Type | Description |
|---|---|---|
| `id` | UUID (path) | Flow ID. |

**Response:** `204 No Content`.

**Errors:**
- `404 Not Found` — Flow does not exist.

---

## Trails

Record build provenance metadata linked to a compliance flow.

### Create a Trail

```
POST /api/v1/trails
```

**Request Body:**

| Field | Type | Required | Description |
|---|---|---|---|
| `flowId` | UUID | Yes | ID of the parent flow. |
| `gitCommitSha` | string | Yes | Git commit SHA. |
| `gitBranch` | string | Yes | Git branch name. |
| `gitAuthor` | string | Yes | Git commit author username. |
| `gitAuthorEmail` | string | Yes | Git commit author email. |
| `pullRequestId` | string | No | Pull request identifier. |
| `pullRequestReviewer` | string | No | Pull request reviewer username. |
| `deploymentActor` | string | No | Who or what triggered the deployment. |

**Example:**

```bash
curl -X POST http://localhost:8080/api/v1/trails \
  -H "Content-Type: application/json" \
  -d '{
    "flowId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "gitCommitSha": "abc123def456",
    "gitBranch": "main",
    "gitAuthor": "jane.doe",
    "gitAuthorEmail": "jane@example.com",
    "pullRequestId": "PR-42",
    "pullRequestReviewer": "john.smith"
  }'
```

**Response:** `200 OK`

```json
{
  "id": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
  "flowId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "gitCommitSha": "abc123def456",
  "gitBranch": "main",
  "gitAuthor": "jane.doe",
  "gitAuthorEmail": "jane@example.com",
  "pullRequestId": "PR-42",
  "pullRequestReviewer": "john.smith",
  "deploymentActor": null,
  "status": "PENDING",
  "createdAt": "2026-03-11T10:01:00Z",
  "updatedAt": "2026-03-11T10:01:00Z"
}
```

**Errors:**
- `404 Not Found` — Referenced flow does not exist.

---

### List All Trails

```
GET /api/v1/trails
```

| Parameter | Type | Description |
|---|---|---|
| `flowId` | UUID (query, optional) | Filter trails by flow ID. |

**Response:** `200 OK` — Array of `TrailResponse`.

---

### Get a Trail

```
GET /api/v1/trails/{id}
```

| Parameter | Type | Description |
|---|---|---|
| `id` | UUID (path) | Trail ID. |

**Response:** `200 OK` — `TrailResponse`.

**Errors:**
- `404 Not Found` — Trail does not exist.

---

### List Trails for a Flow

```
GET /api/v1/flows/{flowId}/trails
```

| Parameter | Type | Description |
|---|---|---|
| `flowId` | UUID (path) | Flow ID. |

**Response:** `200 OK` — Array of `TrailResponse`.

---

## Artifacts

Track container images linked to build trails.

### Report an Artifact

```
POST /api/v1/trails/{trailId}/artifacts
```

| Parameter | Type | Description |
|---|---|---|
| `trailId` | UUID (path) | Trail ID. |

**Request Body:**

| Field | Type | Required | Description |
|---|---|---|---|
| `imageName` | string | Yes | Container image name. |
| `imageTag` | string | Yes | Container image tag (e.g., `v1.0.0`). |
| `sha256Digest` | string | Yes | SHA-256 digest of the container image. |
| `registry` | string | No | Container registry URL (e.g., `ghcr.io/myorg`). |
| `reportedBy` | string | Yes | Who or what reported the artifact (e.g., `ci-pipeline`). |

**Example:**

```bash
curl -X POST http://localhost:8080/api/v1/trails/{trailId}/artifacts \
  -H "Content-Type: application/json" \
  -d '{
    "imageName": "myapp",
    "imageTag": "v1.0.0",
    "sha256Digest": "sha256:abcdef1234567890",
    "registry": "ghcr.io/myorg",
    "reportedBy": "ci-pipeline"
  }'
```

**Response:** `200 OK`

```json
{
  "id": "c3d4e5f6-a7b8-9012-cdef-123456789012",
  "trailId": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
  "imageName": "myapp",
  "imageTag": "v1.0.0",
  "sha256Digest": "sha256:abcdef1234567890",
  "registry": "ghcr.io/myorg",
  "reportedAt": "2026-03-11T10:02:00Z",
  "reportedBy": "ci-pipeline"
}
```

---

### List Artifacts for a Trail

```
GET /api/v1/trails/{trailId}/artifacts
```

| Parameter | Type | Description |
|---|---|---|
| `trailId` | UUID (path) | Trail ID. |

**Response:** `200 OK` — Array of `ArtifactResponse`.

---

### Find Artifacts by SHA-256 Digest

```
GET /api/v1/artifacts?sha256={digest}
```

| Parameter | Type | Description |
|---|---|---|
| `sha256` | string (query) | SHA-256 digest to search for. |

**Response:** `200 OK` — Array of `ArtifactResponse`.

---

## Attestations

Record evidence that a specific compliance requirement was checked.

### Record an Attestation

```
POST /api/v1/trails/{trailId}/attestations
```

| Parameter | Type | Description |
|---|---|---|
| `trailId` | UUID (path) | Trail ID. |

**Request Body:**

| Field | Type | Required | Description |
|---|---|---|---|
| `type` | string | Yes | Attestation type (e.g., `junit`, `snyk`, `trivy`). |
| `status` | string | No | `PASSED`, `FAILED`, or `PENDING`. Defaults to `PENDING`. |
| `details` | string | No | Additional details about the attestation result. |

**Example:**

```bash
curl -X POST http://localhost:8080/api/v1/trails/{trailId}/attestations \
  -H "Content-Type: application/json" \
  -d '{
    "type": "junit",
    "status": "PASSED",
    "details": "247 tests passed, 0 failures"
  }'
```

**Response:** `200 OK`

```json
{
  "id": "d4e5f6a7-b8c9-0123-defa-234567890123",
  "trailId": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
  "type": "junit",
  "status": "PASSED",
  "evidenceFileHash": null,
  "evidenceFileName": null,
  "evidenceFileSizeBytes": null,
  "details": "247 tests passed, 0 failures",
  "createdAt": "2026-03-11T10:03:00Z"
}
```

---

### List Attestations for a Trail

```
GET /api/v1/trails/{trailId}/attestations
```

| Parameter | Type | Description |
|---|---|---|
| `trailId` | UUID (path) | Trail ID. |

**Response:** `200 OK` — Array of `AttestationResponse`.

---

## Evidence Files

Store and verify evidence payloads linked to attestations.

### Upload an Evidence File

```
POST /api/v1/trails/{trailId}/attestations/{attestationId}/evidence
```

| Parameter | Type | Description |
|---|---|---|
| `trailId` | UUID (path) | Trail ID. |
| `attestationId` | UUID (path) | Attestation ID. |

**Request:** `multipart/form-data` with a `file` field.

```bash
curl -X POST \
  http://localhost:8080/api/v1/trails/{trailId}/attestations/{attestationId}/evidence \
  -F "file=@test-report.xml"
```

**Response:** `200 OK`

```json
{
  "id": "e5f6a7b8-c9d0-1234-efab-345678901234",
  "attestationId": "d4e5f6a7-b8c9-0123-defa-234567890123",
  "fileName": "test-report.xml",
  "sha256Hash": "e3b0c44298fc1c149afbf4c8996fb924...",
  "fileSizeBytes": 4096,
  "contentType": "application/xml",
  "storedAt": "2026-03-11T10:04:00Z"
}
```

---

## Compliance Assertion

Query whether an artifact satisfies all requirements for a flow.

### Assert Compliance

```
POST /api/v1/assert
```

**Request Body:**

| Field | Type | Required | Description |
|---|---|---|---|
| `sha256Digest` | string | Yes | SHA-256 digest of the artifact to check. |
| `flowId` | UUID | Yes | ID of the flow to check against. |

**Example:**

```bash
curl -X POST http://localhost:8080/api/v1/assert \
  -H "Content-Type: application/json" \
  -d '{
    "sha256Digest": "sha256:abcdef1234567890",
    "flowId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
  }'
```

**Response:** `200 OK`

```json
{
  "sha256Digest": "sha256:abcdef1234567890",
  "flowId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "COMPLIANT",
  "missingAttestationTypes": [],
  "failedAttestationTypes": [],
  "details": "All required attestation types have passed."
}
```

**Status Values:**

| Status | Description |
|---|---|
| `COMPLIANT` | All required attestation types have a `PASSED` attestation. |
| `NON_COMPLIANT` | One or more required attestations are missing or failed. |

---

## Chain of Custody

Retrieve the full audit trail for an artifact.

### Get Chain of Custody

```
GET /api/v1/compliance/artifact/{sha256Digest}
```

| Parameter | Type | Description |
|---|---|---|
| `sha256Digest` | string (path) | SHA-256 digest of the artifact. |

**Response:** `200 OK`

```json
{
  "artifact": { /* ArtifactResponse */ },
  "trail": { /* TrailResponse */ },
  "flow": { /* FlowResponse */ },
  "attestations": [ /* AttestationResponse[] */ ],
  "evidenceFiles": [ /* EvidenceFileResponse[] */ ],
  "complianceStatus": "COMPLIANT"
}
```

**Errors:**
- `404 Not Found` — No artifact found with that digest.

---

## Error Responses

All errors return a consistent JSON structure:

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Flow not found with id: a1b2c3d4-...",
  "timestamp": "2026-03-11T10:00:00Z"
}
```

| HTTP Status | Error Type | Description |
|---|---|---|
| `400 Bad Request` | `BadRequestException` | Invalid request parameters. |
| `404 Not Found` | `NotFoundException` | Resource does not exist. |
| `409 Conflict` | `ConflictException` | Uniqueness constraint violated (e.g., duplicate flow name). |
| `500 Internal Server Error` | `IntegrityException` or unhandled | Data integrity error or unexpected failure. |

---

## Related Documentation

| Document | Description |
|---|---|
| [README.md](README.md) | Project overview, architecture, and quick start. |
| [USER_GUIDE.md](USER_GUIDE.md) | Comprehensive user guide with setup, use cases, security, and lifecycle management. |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Contribution guidelines and coding conventions. |
| [SECURITY.md](SECURITY.md) | Security policy and vulnerability reporting. |
