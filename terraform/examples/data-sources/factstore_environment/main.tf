data "factstore_environment" "existing_prod" {
  id = "00000000-0000-0000-0000-000000000002"
}

output "environment_type" {
  value = data.factstore_environment.existing_prod.type
}
