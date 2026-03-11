# Factstore User Guide

A comprehensive guide to installing, configuring, and operating the **Factstore** Supply Chain Compliance Fact Store.

---

## Table of Contents

- [1. Setup \& Installation](#1-setup--installation)
  - [1.1 Prerequisites](#11-prerequisites)
  - [1.2 Clone the Repository](#12-clone-the-repository)
  - [1.3 Backend Setup](#13-backend-setup)
  - [1.4 Frontend Setup](#14-frontend-setup)
  - [1.5 Verify the Installation](#15-verify-the-installation)
- [2. Core Concepts](#2-core-concepts)
  - [2.1 Domain Model](#21-domain-model)
  - [2.2 Compliance Workflow](#22-compliance-workflow)
- [3. Use Cases](#3-use-cases)
  - [3.1 Your First Compliance Flow](#31-your-first-compliance-flow)
  - [3.2 Recording a Build Trail](#32-recording-a-build-trail)
  - [3.3 Reporting an Artifact](#33-reporting-an-artifact)
  - [3.4 Submitting Attestations](#34-submitting-attestations)
  - [3.5 Uploading Evidence Files](#35-uploading-evidence-files)
  - [3.6 Asserting Compliance](#36-asserting-compliance)
  - [3.7 Querying the Chain of Custody](#37-querying-the-chain-of-custody)
  - [3.8 Integration Scenarios](#38-integration-scenarios)
- [4. Security \& Data Privacy](#4-security--data-privacy)
  - [4.1 Current Security Model](#41-current-security-model)
  - [4.2 Data Integrity](#42-data-integrity)
  - [4.3 Network Security](#43-network-security)
  - [4.4 Access Control Guidelines](#44-access-control-guidelines)
  - [4.5 Handling Sensitive Data](#45-handling-sensitive-data)
- [5. Lifecycle Management](#5-lifecycle-management)
  - [5.1 Versioning Flows](#51-versioning-flows)
  - [5.2 Updating Existing Entries](#52-updating-existing-entries)
  - [5.3 Deprecating Obsolete Flows](#53-deprecating-obsolete-flows)
  - [5.4 Data Pruning and Archival](#54-data-pruning-and-archival)
  - [5.5 Database Considerations](#55-database-considerations)
- [6. Related Documentation](#6-related-documentation)

---

## 1. Setup & Installation

### 1.1 Prerequisites

| Requirement | Version | Verification Command |
|---|---|---|
| **Java** (Eclipse Temurin recommended) | 21 | `java -version` |
| **Node.js** | 20 | `node --version` |
| **npm** | (bundled with Node.js) | `npm --version` |

> **Pro-Tip:** Use a version manager such as [SDKMAN!](https://sdkman.io/) for Java or [nvm](https://github.com/nvm-sh/nvm) for Node.js to switch between versions without affecting your system installation.

### 1.2 Clone the Repository

```bash
git clone https://github.com/MaximumTrainer/Factstore.git
cd Factstore
```

### 1.3 Backend Setup

The backend is a Spring Boot application built with Gradle. No external database setup is required — it uses an embedded H2 in-memory database.

```bash
cd backend

# Build and run tests
./gradlew build

# Start the backend server (port 8080)
./gradlew bootRun
```

- [x] The backend starts on **http://localhost:8080**
- [x] Swagger UI is available at **http://localhost:8080/swagger-ui.html**
- [x] OpenAPI JSON spec is at **http://localhost:8080/api-docs**
- [x] H2 Console (dev only) is at **http://localhost:8080/h2-console** (username: `sa`, no password)

> **Pro-Tip:** You can also run the compiled JAR directly:
> ```bash
> java -jar backend/build/libs/factstore-0.0.1-SNAPSHOT.jar
> ```

### 1.4 Frontend Setup

```bash
cd frontend

# Install dependencies (use ci for reproducible builds)
npm ci

# Start the dev server (port 5173)
npm run dev
```

- [x] The frontend starts on **http://localhost:5173**
- [x] API requests are proxied to the backend at `http://localhost:8080`

> **Note:** The backend must be running before the frontend can load data.

### 1.5 Verify the Installation

Once both services are running, verify the installation end-to-end:

**Step 1 — Health-check the API:**

```bash
curl -s http://localhost:8080/api/v1/flows | head
```

Expected: an empty JSON array `[]` (no flows created yet).

**Step 2 — Create your first flow (the "Hello World" of Factstore):**

```bash
curl -s -X POST http://localhost:8080/api/v1/flows \
  -H "Content-Type: application/json" \
  -d '{
    "name": "hello-world-flow",
    "description": "My first compliance flow",
    "requiredAttestationTypes": ["unit-test"]
  }'
```

Expected: a JSON response with the created flow, including a generated `id`.

**Step 3 — Open the frontend:**

Navigate to [http://localhost:5173/flows](http://localhost:5173/flows). You should see `hello-world-flow` listed.

---

## 2. Core Concepts

### 2.1 Domain Model

```
Flow  ──<  Trail  ──<  Artifact
                 └──<  Attestation  ──>  EvidenceFile
```

| Concept | Description |
|---|---|
| **Flow** | A named compliance policy that lists the required attestation types (e.g., `junit`, `snyk`, `trivy`). |
| **Trail** | A build record capturing Git provenance metadata (commit SHA, branch, author, PR) linked to a Flow. |
| **Artifact** | A container image identified by SHA-256 digest, name, tag, and registry, linked to a Trail. |
| **Attestation** | Evidence that a specific requirement was checked, with a status of `PASSED`, `FAILED`, or `PENDING`. |
| **EvidenceFile** | The actual evidence payload (e.g., a test report PDF) stored with its SHA-256 hash for integrity verification. |

### 2.2 Compliance Workflow

The typical workflow follows this sequence:

1. **Define a Flow** — specify what attestation types are required.
2. **Begin a Trail** — record Git metadata for a build.
3. **Report Artifacts** — register the container images produced by the build.
4. **Submit Attestations** — attach evidence (test results, scan reports) with PASSED/FAILED status.
5. **Upload Evidence** — optionally store the actual evidence files in the Evidence Vault.
6. **Assert Compliance** — query whether an artifact satisfies all requirements for a flow.

---

## 3. Use Cases

All examples use `curl`. Replace `localhost:8080` with your deployment URL as needed. For the full API reference, see [API_REFERENCE.md](API_REFERENCE.md).

### 3.1 Your First Compliance Flow

Create a flow that requires unit tests and a container vulnerability scan:

```bash
curl -s -X POST http://localhost:8080/api/v1/flows \
  -H "Content-Type: application/json" \
  -d '{
    "name": "production-deploy",
    "description": "Requirements for production deployment",
    "requiredAttestationTypes": ["junit", "trivy"]
  }'
```

**Response:**

```json
{
  "id": "a1b2c3d4-...",
  "name": "production-deploy",
  "description": "Requirements for production deployment",
  "requiredAttestationTypes": ["junit", "trivy"],
  "createdAt": "2026-03-11T10:00:00Z",
  "updatedAt": "2026-03-11T10:00:00Z"
}
```

List all flows:

```bash
curl -s http://localhost:8080/api/v1/flows
```

Retrieve a specific flow by ID:

```bash
curl -s http://localhost:8080/api/v1/flows/{flowId}
```

### 3.2 Recording a Build Trail

After a CI build completes, record a trail with Git provenance:

```bash
curl -s -X POST http://localhost:8080/api/v1/trails \
  -H "Content-Type: application/json" \
  -d '{
    "flowId": "FLOW_ID_HERE",
    "gitCommitSha": "abc123def456",
    "gitBranch": "main",
    "gitAuthor": "jane.doe",
    "gitAuthorEmail": "jane@example.com",
    "pullRequestId": "PR-42",
    "pullRequestReviewer": "john.smith",
    "deploymentActor": "ci-bot"
  }'
```

List trails for a specific flow:

```bash
curl -s http://localhost:8080/api/v1/flows/{flowId}/trails
```

### 3.3 Reporting an Artifact

Report a container image produced by the build:

```bash
curl -s -X POST http://localhost:8080/api/v1/trails/{trailId}/artifacts \
  -H "Content-Type: application/json" \
  -d '{
    "imageName": "myapp",
    "imageTag": "v1.0.0",
    "sha256Digest": "sha256:abcdef1234567890...",
    "registry": "ghcr.io/myorg",
    "reportedBy": "ci-pipeline"
  }'
```

Look up artifacts by SHA-256 digest:

```bash
curl -s "http://localhost:8080/api/v1/artifacts?sha256=sha256:abcdef1234567890..."
```

### 3.4 Submitting Attestations

Record that unit tests passed:

```bash
curl -s -X POST http://localhost:8080/api/v1/trails/{trailId}/attestations \
  -H "Content-Type: application/json" \
  -d '{
    "type": "junit",
    "status": "PASSED",
    "details": "247 tests passed, 0 failures"
  }'
```

Record that a Trivy scan passed:

```bash
curl -s -X POST http://localhost:8080/api/v1/trails/{trailId}/attestations \
  -H "Content-Type: application/json" \
  -d '{
    "type": "trivy",
    "status": "PASSED",
    "details": "No critical or high vulnerabilities found"
  }'
```

### 3.5 Uploading Evidence Files

Attach an evidence file (e.g., a test report) to an attestation:

```bash
curl -s -X POST \
  http://localhost:8080/api/v1/trails/{trailId}/attestations/{attestationId}/evidence \
  -F "file=@test-report.xml"
```

The response includes the SHA-256 hash of the uploaded file for integrity verification:

```json
{
  "id": "...",
  "attestationId": "...",
  "fileName": "test-report.xml",
  "sha256Hash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
  "fileSizeBytes": 4096,
  "contentType": "application/xml",
  "storedAt": "2026-03-11T10:05:00Z"
}
```

### 3.6 Asserting Compliance

Check whether an artifact meets all requirements for a given flow:

```bash
curl -s -X POST http://localhost:8080/api/v1/assert \
  -H "Content-Type: application/json" \
  -d '{
    "sha256Digest": "sha256:abcdef1234567890...",
    "flowId": "FLOW_ID_HERE"
  }'
```

**Compliant response:**

```json
{
  "sha256Digest": "sha256:abcdef1234567890...",
  "flowId": "...",
  "status": "COMPLIANT",
  "missingAttestationTypes": [],
  "failedAttestationTypes": [],
  "details": "All required attestation types have passed."
}
```

**Non-compliant response:**

```json
{
  "sha256Digest": "sha256:abcdef1234567890...",
  "flowId": "...",
  "status": "NON_COMPLIANT",
  "missingAttestationTypes": ["trivy"],
  "failedAttestationTypes": [],
  "details": "Missing attestations: trivy"
}
```

### 3.7 Querying the Chain of Custody

Retrieve the full chain of custody for an artifact:

```bash
curl -s http://localhost:8080/api/v1/compliance/artifact/{sha256Digest}
```

This returns the complete audit trail: the artifact, its trail, the parent flow, all attestations, evidence files, and the computed compliance status.

### 3.8 Integration Scenarios

#### CI/CD Pipeline Integration

Factstore is designed to be called from CI/CD pipelines. A typical GitHub Actions integration:

```yaml
# .github/workflows/compliance.yml
jobs:
  compliance:
    runs-on: ubuntu-latest
    steps:
      - name: Create trail
        run: |
          TRAIL_ID=$(curl -s -X POST $FACTSTORE_URL/api/v1/trails \
            -H "Content-Type: application/json" \
            -d '{
              "flowId": "'$FLOW_ID'",
              "gitCommitSha": "'$GITHUB_SHA'",
              "gitBranch": "'$GITHUB_REF_NAME'",
              "gitAuthor": "'$GITHUB_ACTOR'",
              "gitAuthorEmail": "'$GITHUB_ACTOR'@users.noreply.github.com",
              "pullRequestId": "'$PR_NUMBER'"
            }' | jq -r '.id')
          echo "TRAIL_ID=$TRAIL_ID" >> $GITHUB_ENV

      - name: Report artifact
        run: |
          curl -s -X POST $FACTSTORE_URL/api/v1/trails/$TRAIL_ID/artifacts \
            -H "Content-Type: application/json" \
            -d '{
              "imageName": "myapp",
              "imageTag": "'$IMAGE_TAG'",
              "sha256Digest": "'$IMAGE_DIGEST'",
              "registry": "ghcr.io/myorg",
              "reportedBy": "github-actions"
            }'

      - name: Submit test attestation
        run: |
          curl -s -X POST $FACTSTORE_URL/api/v1/trails/$TRAIL_ID/attestations \
            -H "Content-Type: application/json" \
            -d '{
              "type": "junit",
              "status": "PASSED",
              "details": "All tests passed"
            }'

      - name: Gate deployment
        run: |
          RESULT=$(curl -s -X POST $FACTSTORE_URL/api/v1/assert \
            -H "Content-Type: application/json" \
            -d '{
              "sha256Digest": "'$IMAGE_DIGEST'",
              "flowId": "'$FLOW_ID'"
            }')
          STATUS=$(echo $RESULT | jq -r '.status')
          if [ "$STATUS" != "COMPLIANT" ]; then
            echo "Compliance check failed!"
            echo $RESULT | jq .
            exit 1
          fi
```

#### Verification Layer for RAG Pipelines

Factstore can serve as a verification layer in Retrieval-Augmented Generation (RAG) systems to ensure that only compliance-verified artifacts are referenced:

1. **Store compliance facts** for each artifact as it moves through the pipeline.
2. **Query the assert endpoint** before allowing an artifact reference into the context window.
3. **Use chain of custody** to provide provenance metadata alongside retrieved facts.

#### Long-term Memory for LLM Agents

Autonomous agents can interact with Factstore to maintain a persistent, verifiable record of build and deployment decisions:

1. **Agent records a trail** when initiating a build.
2. **Agent submits attestations** as checks complete.
3. **Agent queries compliance** before approving a deployment.
4. **Agent retrieves chain of custody** for audit reporting.

See the [API Reference](API_REFERENCE.md) for full endpoint documentation.

---

## 4. Security & Data Privacy

### 4.1 Current Security Model

Factstore is currently designed as an **internal development tool** without built-in authentication or authorization. The API is open by default.

> **Pro-Tip:** For production deployments, place Factstore behind a reverse proxy (e.g., NGINX, Envoy, or a cloud API gateway) that handles authentication, TLS termination, and rate limiting. See [SECURITY.md](SECURITY.md) for detailed recommendations.

### 4.2 Data Integrity

Factstore ensures data integrity through several mechanisms:

| Mechanism | Description |
|---|---|
| **SHA-256 hashing** | Artifact digests and evidence files are identified by SHA-256 hashes, enabling tamper detection. |
| **Immutable timestamps** | All entities have creation timestamps (`createdAt`, `reportedAt`, `storedAt`) that cannot be modified. |
| **Unique constraints** | Flow names are unique; duplicate creation attempts return `409 Conflict`. |
| **Referential integrity** | Trails reference Flows, Attestations reference Trails, and Evidence Files reference Attestations via UUID foreign keys. |

### 4.3 Network Security

| Concern | Current State | Recommendation |
|---|---|---|
| **Transport encryption** | Not enforced (HTTP) | Terminate TLS at a reverse proxy or load balancer. |
| **CORS** | Allows `http://localhost:5173` only | Restrict to your frontend domain in production. Update `CorsConfig.kt`. |
| **API authentication** | None | Add OAuth 2.0, API keys, or mTLS via a gateway. |

### 4.4 Access Control Guidelines

For production deployments, implement the following access control layers:

1. **API Gateway Authentication** — Use an API gateway to enforce authentication (e.g., OAuth 2.0 bearer tokens, API keys).
2. **Role-Based Access** — Define roles for read-only consumers, CI pipeline writers, and administrators.
3. **Network Segmentation** — Run Factstore in a private network segment accessible only from CI runners and authorized services.

### 4.5 Handling Sensitive Data

- **Evidence files** may contain sensitive security scan results. Ensure the storage backend is encrypted at rest when deploying with a persistent database.
- **Git metadata** (author emails, commit SHAs) is stored in trails. Apply data retention policies consistent with your organization's privacy requirements.
- **H2 in-memory database** — Data is lost on restart by default. For production, switch to a persistent database with encryption at rest (see [Section 5.5](#55-database-considerations)).

> **Pro-Tip:** Never store secrets (API keys, passwords, private keys) as attestation details or evidence files. Use a dedicated secrets manager and store only references in Factstore.

---

## 5. Lifecycle Management

### 5.1 Versioning Flows

Flows can be updated to add, remove, or modify required attestation types:

```bash
curl -s -X PUT http://localhost:8080/api/v1/flows/{flowId} \
  -H "Content-Type: application/json" \
  -d '{
    "requiredAttestationTypes": ["junit", "trivy", "snyk"]
  }'
```

> **Pro-Tip:** When adding new attestation types to a flow, existing trails are not retroactively affected. Only new compliance assertions will check against the updated requirements. Consider creating a new flow (e.g., `production-deploy-v2`) to maintain a clear audit trail of policy changes.

### 5.2 Updating Existing Entries

| Entity | Update Support | Method |
|---|---|---|
| **Flow** | Name, description, required attestation types | `PUT /api/v1/flows/{id}` |
| **Trail** | Immutable after creation | — |
| **Artifact** | Immutable after creation | — |
| **Attestation** | Immutable after creation | — |
| **EvidenceFile** | Immutable after creation | — |

Trails, artifacts, attestations, and evidence files are **immutable by design** — once recorded, they form a tamper-evident audit trail. To correct a mistake, create a new entry rather than modifying an existing one.

### 5.3 Deprecating Obsolete Flows

To retire an obsolete flow:

1. **Stop creating new trails** against the flow.
2. **Optionally rename** the flow to indicate its deprecated status:
   ```bash
   curl -s -X PUT http://localhost:8080/api/v1/flows/{flowId} \
     -H "Content-Type: application/json" \
     -d '{"name": "DEPRECATED-old-flow-name"}'
   ```
3. **Delete the flow** when it is no longer referenced:
   ```bash
   curl -s -X DELETE http://localhost:8080/api/v1/flows/{flowId}
   ```

> **Pro-Tip:** Before deleting a flow, export any compliance reports you may need. Once deleted, associated queries will return `404 Not Found`.

### 5.4 Data Pruning and Archival

With the default H2 in-memory database, data is automatically cleared on server restart. For persistent storage deployments:

- **Retention policy** — Define how long trails and attestations should be retained (e.g., 90 days for staging, 7 years for production compliance records).
- **Archival** — Export historical data via the API before pruning:
  ```bash
  # Export all trails for a flow
  curl -s http://localhost:8080/api/v1/flows/{flowId}/trails > trails-backup.json
  ```
- **Pruning** — Implement scheduled cleanup at the database level to remove trails older than your retention period.

### 5.5 Database Considerations

| Configuration | Use Case |
|---|---|
| **H2 in-memory** (default) | Development and testing. Data is lost on restart. |
| **H2 file-based** | Simple persistence. Change `jdbc:h2:mem:factstore` to `jdbc:h2:file:./data/factstore` in `application.yml`. |
| **PostgreSQL / MySQL** | Production deployments. Add the JDBC driver dependency and update `application.yml`. |

To switch to a persistent H2 file database, update `backend/src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/factstore;DB_CLOSE_DELAY=-1
  jpa:
    hibernate:
      ddl-auto: update  # Change from create-drop to update
```

---

## 6. Related Documentation

| Document | Description |
|---|---|
| [README.md](README.md) | Project overview, architecture, and quick start. |
| [API_REFERENCE.md](API_REFERENCE.md) | Complete REST API reference with all endpoints, schemas, and examples. |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Contribution guidelines, coding conventions, and pull request process. |
| [SECURITY.md](SECURITY.md) | Security policy, vulnerability reporting, and deployment hardening. |
| [BACKLOG.md](BACKLOG.md) | Feature backlog and roadmap. |
