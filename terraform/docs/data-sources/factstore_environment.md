# data.factstore_environment

Reads an existing Factstore Environment by ID. Use this data source to reference environments managed outside of Terraform.

## Example Usage

```hcl
data "factstore_environment" "existing_prod" {
  id = "00000000-0000-0000-0000-000000000002"
}

output "environment_type" {
  value = data.factstore_environment.existing_prod.type
}
```

## Argument Reference

- `id` (Required) — The UUID of the environment to read.

## Attributes Reference

- `name` — The name of the environment.
- `type` — The type of the environment (`K8S`, `ECS`, `VM`, `PHYSICAL`, `SERVERLESS`).
- `description` — The description of the environment.
- `created_at` — RFC3339 timestamp when the environment was created.
- `updated_at` — RFC3339 timestamp when the environment was last updated.
