terraform {
  required_providers {
    factstore = {
      source  = "MaximumTrainer/factstore"
      version = "~> 1.0"
    }
  }
}

provider "factstore" {
  base_url  = "https://factstore.example.com"
  api_token = var.factstore_api_token
}

variable "factstore_api_token" {
  description = "API token for authenticating with Factstore"
  type        = string
  sensitive   = true
}
