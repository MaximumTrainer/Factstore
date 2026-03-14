# factstore_flow

Manages a Factstore Flow. A Flow defines a compliance pipeline with required attestation types that every Trail must satisfy.

## Example Usage

```hcl
resource "factstore_flow" "backend_ci" {
  name        = "backend-ci"
  description = "CI pipeline for the backend service"
  required_attestation_types = ["junit", "snyk", "pull-request"]
}
```

## Argument Reference

- `name` (Required) — The unique name of the flow.
- `description` (Optional) — A human-readable description of the flow.
- `required_attestation_types` (Optional) — List of attestation types required for trail compliance.

## Attributes Reference

In addition to all arguments above, the following attributes are exported:

- `id` — The UUID of the flow.
- `created_at` — RFC3339 timestamp when the flow was created.
- `updated_at` — RFC3339 timestamp when the flow was last updated.

## Import

Flows can be imported using their UUID:

```sh
terraform import factstore_flow.backend_ci <flow-uuid>
```
