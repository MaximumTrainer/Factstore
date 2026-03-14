# factstore_logical_environment

Manages a Factstore Logical Environment. Logical Environments provide an abstraction layer for grouping physical environments for policy enforcement and reporting.

## Example Usage

```hcl
resource "factstore_logical_environment" "prod_group" {
  name        = "production-group"
  description = "Logical grouping of all production environments"
}
```

## Argument Reference

- `name` (Required) — The unique name of the logical environment.
- `description` (Optional) — A human-readable description of the logical environment.

## Attributes Reference

In addition to all arguments above, the following attributes are exported:

- `id` — The UUID of the logical environment.
- `created_at` — RFC3339 timestamp when the logical environment was created.
- `updated_at` — RFC3339 timestamp when the logical environment was last updated.

## Import

Logical environments can be imported using their UUID:

```sh
terraform import factstore_logical_environment.prod_group <logical-env-uuid>
```
