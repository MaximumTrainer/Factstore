data "factstore_flow" "existing_ci" {
  id = "00000000-0000-0000-0000-000000000001"
}

output "flow_name" {
  value = data.factstore_flow.existing_ci.name
}
