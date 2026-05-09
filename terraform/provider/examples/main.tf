terraform {
  required_providers {
    factstore = {
      source  = "registry.terraform.io/MaximumTrainer/factstore"
      version = "~> 0.1"
    }
  }
}

provider "factstore" {
  endpoint = "http://localhost:8080"
}

resource "factstore_flow" "example" {
  name        = "my-release-flow"
  description = "Release gating flow for the payments service"
  org_slug    = "acme"
}

resource "factstore_environment" "example" {
  name        = "production"
  description = "Production Kubernetes cluster"
  org_slug    = "acme"
}
