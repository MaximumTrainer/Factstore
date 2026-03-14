# factstore_policy_attachment

Attaches a Factstore Policy to an Environment. Once attached, the policy's compliance rules are enforced for deployments to that environment.

## Example Usage

```hcl
resource "factstore_policy_attachment" "prod" {
  policy_id      = factstore_policy.prod_policy.id
  environment_id = factstore_environment.production.id
}
```

## Argument Reference

- `policy_id` (Required, Forces new resource) — The UUID of the policy to attach.
- `environment_id` (Required, Forces new resource) — The UUID of the environment to attach the policy to.

## Attributes Reference

In addition to all arguments above, the following attributes are exported:

- `id` — The UUID of the policy attachment.
- `created_at` — RFC3339 timestamp when the attachment was created.

## Import

Policy attachments can be imported using their UUID:

```sh
terraform import factstore_policy_attachment.prod <attachment-uuid>
```
