# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| `main` branch (latest) | ✅ Active development |
| Older tagged releases | ⚠️ Best-effort only |

We recommend always running the latest commit on `main` or the latest tagged release.

---

## Reporting a Vulnerability

If you discover a security vulnerability in OpenFactstore, please **do not open a public GitHub issue**.

### Preferred method: GitHub Security Advisory

1. Go to the [Security tab](https://github.com/MaximumTrainer/OpenFactstore/security) of the repository.
2. Click **"Report a vulnerability"**.
3. Fill in the advisory form with:
   - A clear description of the vulnerability
   - Steps to reproduce
   - Potential impact
   - Any suggested mitigations

We will acknowledge receipt within **48 hours** and aim to provide a fix or mitigation within **14 days** for critical issues.

### Alternative: Email

If you prefer email, send details to the repository maintainers. You can find contact information in the repository profile.

---

## Known Limitations

### No built-in authentication enforcement (current version)

> ⚠️ The API currently trusts all inbound requests. The API key and service account infrastructure is implemented (`POST /api/v1/api-keys`, `POST /api/v1/service-accounts`), but key enforcement is not mandated by default in the current release.

**Mitigation for production deployments:**

- Place OpenFactstore behind an API gateway or reverse proxy that enforces authentication.
- Restrict network access using firewall rules, security groups, or Kubernetes NetworkPolicy so only CI/CD systems and approved clients can reach the API.
- Use the service account + API key feature and validate `X-Api-Key` at the proxy layer.

### SCM tokens stored Base64-encoded (without Vault)

When `VAULT_ENABLED=false`, SCM integration tokens (GitHub, GitLab) are stored Base64-encoded in PostgreSQL. This is encoding, not encryption.

**Mitigation:** Set `VAULT_ENABLED=true` and configure HashiCorp Vault for all environments that store real SCM tokens.

### H2 in-memory database (unit tests only)

Unit tests run against an H2 in-memory database. H2 must never be used in production — use PostgreSQL 16.

---

## Production Hardening Checklist

Use this checklist before deploying OpenFactstore to a production environment.

### Network

- [ ] API is not exposed directly to the public internet.
- [ ] API is placed behind a TLS-terminating reverse proxy (nginx, Caddy, AWS ALB).
- [ ] TLS 1.2+ is enforced; TLS 1.0/1.1 and weak cipher suites are disabled.
- [ ] Firewall rules restrict access to port 8080 to known CI/CD IPs and internal networks.
- [ ] PostgreSQL (5432) and Vault (8200) are not exposed outside the private network.

### Authentication & Authorisation

- [ ] API key enforcement is applied at the reverse proxy or application layer.
- [ ] Unique service accounts and API keys are created per CI pipeline.
- [ ] API keys are rotated at least every 90 days.
- [ ] Default Grafana password (`changeme`) has been changed.

### Secrets Management

- [ ] `DB_PASSWORD` is stored in a secrets manager (AWS Secrets Manager, GCP Secret Manager, Vault).
- [ ] `VAULT_TOKEN` is stored in a secrets manager; consider Vault auto-unseal via KMS.
- [ ] SCM tokens are stored in Vault (`VAULT_ENABLED=true`), not plain PostgreSQL.
- [ ] No secrets are committed to source control.

### Database

- [ ] PostgreSQL is running with encryption-at-rest enabled.
- [ ] Regular automated backups are configured and tested.
- [ ] Database user `factstore` has minimum required privileges (no superuser).
- [ ] Flyway migrations are the only mechanism for schema changes.

### Observability

- [ ] Prometheus and Grafana are not exposed to the public internet.
- [ ] Grafana is secured with SSO or strong admin credentials.
- [ ] Alerting is configured for `factstore_assert_noncompliant_total` spikes.
- [ ] Audit log (`GET /api/v1/audit`) is monitored for anomalous access patterns.

### Updates

- [ ] A process exists to track and apply dependency updates (Dependabot or Renovate).
- [ ] Container images are rebuilt regularly to pick up OS patch updates.
- [ ] The Dockerfile base image (`eclipse-temurin:21-jre-alpine`) is pinned by digest in production.
