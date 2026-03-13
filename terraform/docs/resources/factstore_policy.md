# factstore_policy

Manages a Factstore Policy. Policies define compliance requirements for environments, including provenance enforcement and required attestation types.

## Example Usage

```hcl
resource "factstore_policy" "prod_policy" {
  name                     = "prod-requirements"
  enforce_provenance       = true
  enforce_trail_compliance = true
  required_attestation_types = ["snyk", "junit"]
}
```

## Argument Reference

- `name` (Required) — The unique name of the policy.
- `enforce_provenance` (Optional) — Whether to enforce artifact provenance checks. Defaults to `false`.
- `enforce_trail_compliance` (Optional) — Whether to enforce trail compliance checks. Defaults to `false`.
- `required_attestation_types` (Optional) — List of attestation types required by this policy.

## Attributes Reference

In addition to all arguments above, the following attributes are exported:

- `id` — The UUID of the policy.
- `created_at` — RFC3339 timestamp when the policy was created.
- `updated_at` — RFC3339 timestamp when the policy was last updated.

## Import

Policies can be imported using their UUID:

```sh
terraform import factstore_policy.prod_policy <policy-uuid>
```
