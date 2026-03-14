resource "factstore_environment" "production" {
  name        = "production"
  type        = "K8S"
  description = "Production Kubernetes cluster"
}
