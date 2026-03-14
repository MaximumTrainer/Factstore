# factstore_environment

Manages a Factstore Environment. Environments represent deployment targets such as Kubernetes clusters, S3 buckets, or Lambda functions.

## Example Usage

```hcl
resource "factstore_environment" "production" {
  name        = "production"
  type        = "K8S"
  description = "Production Kubernetes cluster"
}
```

## Argument Reference

- `name` (Required) — The unique name of the environment.
- `type` (Required) — The type of the environment. Allowed values: `K8S`, `S3`, `LAMBDA`, `GENERIC`.
- `description` (Optional) — A human-readable description of the environment.

## Attributes Reference

In addition to all arguments above, the following attributes are exported:

- `id` — The UUID of the environment.
- `created_at` — RFC3339 timestamp when the environment was created.
- `updated_at` — RFC3339 timestamp when the environment was last updated.

## Import

Environments can be imported using their UUID:

```sh
terraform import factstore_environment.production <environment-uuid>
```
