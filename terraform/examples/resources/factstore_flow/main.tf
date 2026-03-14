resource "factstore_flow" "backend_ci" {
  name        = "backend-ci"
  description = "CI pipeline for the backend service"
  required_attestation_types = ["junit", "snyk", "pull-request"]
}
