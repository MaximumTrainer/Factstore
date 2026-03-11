# Security Policy

This document describes the security model, vulnerability reporting process, and deployment hardening recommendations for Factstore.

---

## Table of Contents

- [Supported Versions](#supported-versions)
- [Reporting a Vulnerability](#reporting-a-vulnerability)
- [Current Security Model](#current-security-model)
- [Data Integrity](#data-integrity)
- [Deployment Hardening](#deployment-hardening)
- [Best Practices](#best-practices)

---

## Supported Versions

| Version | Supported |
|---|---|
| `main` branch (latest) | ✅ |
| Older releases | ❌ |

---

## Reporting a Vulnerability

If you discover a security vulnerability, please report it responsibly:

1. **Do not** open a public GitHub issue.
2. Email the maintainers with a detailed description of the vulnerability, including steps to reproduce.
3. Allow reasonable time for a fix before public disclosure.

---

## Current Security Model

Factstore is designed as an **internal development tool** for tracking supply chain compliance. The current version does not include built-in authentication or authorization.

| Component | Status |
|---|---|
| API authentication | Not implemented — API is open by default. |
| Authorization / RBAC | Not implemented. |
| Transport encryption (TLS) | Not enforced — runs over HTTP. |
| Data encryption at rest | Depends on storage backend (H2 in-memory by default). |
| CORS | Restricted to `http://localhost:5173` (frontend dev server). |
| Input validation | Basic validation via Spring framework; custom exceptions for constraint violations. |
| Audit logging | Immutable entities with timestamps provide a basic audit trail. |

> **Important:** For any deployment beyond local development, implement the hardening measures described below.

---

## Data Integrity

Factstore provides several data integrity mechanisms:

- **SHA-256 hashing** — Artifact digests and evidence files are identified by SHA-256 hashes, enabling tamper detection.
- **Immutable records** — Trails, artifacts, attestations, and evidence files cannot be modified after creation, forming a tamper-evident chain.
- **Unique constraints** — Flow names must be unique; duplicates return `409 Conflict`.
- **Referential integrity** — All entities are linked via UUID foreign keys (Trail → Flow, Attestation → Trail, EvidenceFile → Attestation).
- **Cryptographic timestamping** — Evidence files are stamped at storage time with their SHA-256 hash.

---

## Deployment Hardening

For production deployments, implement the following security controls:

### Transport Security (TLS)

Terminate TLS at a reverse proxy or load balancer in front of Factstore:

```nginx
# Example NGINX configuration
server {
    listen 443 ssl;
    server_name factstore.internal.example.com;

    ssl_certificate     /etc/ssl/certs/factstore.crt;
    ssl_certificate_key /etc/ssl/private/factstore.key;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### API Authentication

Add an authentication layer using one of these approaches:

| Approach | Use Case |
|---|---|
| **API Gateway** (e.g., Kong, AWS API Gateway) | Centralized authentication with OAuth 2.0 or API key validation. |
| **Reverse Proxy Auth** (e.g., NGINX + OAuth2 Proxy) | Authenticate at the proxy level before requests reach Factstore. |
| **Spring Security** | Add the `spring-boot-starter-security` dependency for built-in authentication. |

### CORS Configuration

For production, update `CorsConfig.kt` to restrict allowed origins:

```kotlin
config.addAllowedOrigin("https://factstore.internal.example.com")
```

### Network Segmentation

- Run Factstore in a private network segment accessible only from CI/CD runners and authorized services.
- Use firewall rules to restrict access to the API port (8080).

### Persistent Storage Encryption

When switching from H2 in-memory to a persistent database:

- Enable encryption at rest on the database server (e.g., PostgreSQL Transparent Data Encryption, AWS RDS encryption).
- Use encrypted connections (TLS) between the application and database.

---

## Best Practices

- **Never store secrets** (API keys, passwords, private keys) as attestation details or evidence files. Use a dedicated secrets manager and store only references.
- **Apply data retention policies** consistent with your organization's compliance requirements. See the [User Guide — Lifecycle Management](USER_GUIDE.md#5-lifecycle-management) for data pruning strategies.
- **Monitor API access** using your reverse proxy or API gateway's logging capabilities.
- **Rotate credentials** for any database or external service connections on a regular schedule.
- **Keep dependencies updated** by monitoring for security advisories in Spring Boot, H2, and frontend packages.

---

## Related Documentation

| Document | Description |
|---|---|
| [USER_GUIDE.md](USER_GUIDE.md) | Setup, use cases, security overview, and lifecycle management. |
| [API_REFERENCE.md](API_REFERENCE.md) | Complete REST API reference. |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Contribution guidelines. |
| [README.md](README.md) | Project overview and architecture. |
