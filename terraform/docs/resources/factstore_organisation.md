# factstore_organisation

Manages a Factstore Organisation. Organisations are the top-level grouping for teams and members in Factstore.

## Example Usage

```hcl
resource "factstore_organisation" "acme" {
  slug        = "acme"
  name        = "ACME Corp"
  description = "ACME Corporation's Factstore organisation"
}
```

## Argument Reference

- `slug` (Required, Forces new resource) — The unique URL-friendly slug for the organisation.
- `name` (Required) — The display name of the organisation.
- `description` (Optional) — A human-readable description of the organisation.

## Attributes Reference

In addition to all arguments above, the following attributes are exported:

- `id` — The UUID of the organisation.
- `created_at` — RFC3339 timestamp when the organisation was created.
- `updated_at` — RFC3339 timestamp when the organisation was last updated.

## Import

Organisations can be imported using their UUID:

```sh
terraform import factstore_organisation.acme <organisation-uuid>
```
