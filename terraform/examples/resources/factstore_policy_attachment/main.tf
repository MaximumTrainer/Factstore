resource "factstore_policy_attachment" "prod" {
  policy_id      = factstore_policy.prod_policy.id
  environment_id = factstore_environment.production.id
}
