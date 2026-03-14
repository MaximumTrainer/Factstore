# data.factstore_flow

Reads an existing Factstore Flow by ID. Use this data source to reference flows managed outside of Terraform.

## Example Usage

```hcl
data "factstore_flow" "existing_ci" {
  id = "00000000-0000-0000-0000-000000000001"
}

output "flow_name" {
  value = data.factstore_flow.existing_ci.name
}
```

## Argument Reference

- `id` (Required) — The UUID of the flow to read.

## Attributes Reference

- `name` — The name of the flow.
- `description` — The description of the flow.
- `required_attestation_types` — List of required attestation types.
- `created_at` — RFC3339 timestamp when the flow was created.
- `updated_at` — RFC3339 timestamp when the flow was last updated.
