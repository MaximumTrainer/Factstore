# OpenFactstore Terraform Provider

A Terraform provider for managing [OpenFactstore](https://github.com/MaximumTrainer/OpenFactstore) resources — a Supply Chain Compliance Fact Store.

## Requirements

- [Terraform](https://developer.hashicorp.com/terraform/downloads) >= 1.5
- [Go](https://golang.org/doc/install) >= 1.21 (to build from source)

## Building from source

```bash
cd terraform/provider
go mod tidy
go build ./...
```

## Provider configuration

```hcl
provider "factstore" {
  endpoint = "http://localhost:8080"  # optional, defaults to http://localhost:8080
}
```

## Resources

### `factstore_flow`

Manages a Factstore Flow — a named template that defines required attestation types for an artifact's release pipeline.

```hcl
resource "factstore_flow" "example" {
  name        = "my-release-flow"
  description = "Release gating flow for the payments service"
  org_slug    = "acme"
}
```

**Attributes:**

| Name | Type | Required | Description |
|------|------|----------|-------------|
| `id` | string | computed | Unique identifier (UUID) |
| `name` | string | yes | Flow name |
| `description` | string | no | Human-readable description |
| `org_slug` | string | yes | Organisation slug |

### `factstore_environment`

Manages a Factstore Environment — a deployment target (e.g. a Kubernetes cluster or ECS service) that artifacts are deployed into.

```hcl
resource "factstore_environment" "example" {
  name        = "production"
  description = "Production Kubernetes cluster"
  org_slug    = "acme"
}
```

**Attributes:**

| Name | Type | Required | Description |
|------|------|----------|-------------|
| `id` | string | computed | Unique identifier (UUID) |
| `name` | string | yes | Environment name |
| `description` | string | no | Human-readable description |
| `org_slug` | string | yes | Organisation slug |

> **Note:** The Factstore API does not expose delete endpoints for flows or environments. Destroying these resources removes them from Terraform state only.

## Example

See [`examples/main.tf`](examples/main.tf) for a complete example.
