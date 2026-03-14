resource "factstore_policy" "prod_policy" {
  name                     = "prod-requirements"
  enforce_provenance       = true
  enforce_trail_compliance = true
  required_attestation_types = ["snyk", "junit"]
}
