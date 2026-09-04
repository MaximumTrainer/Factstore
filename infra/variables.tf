variable "factstore_url" {
  description = "Base URL of the Factstore API (e.g. https://factstore.example.com). Overridden by FACTSTORE_BASE_URL env var."
  type        = string
  default     = "http://localhost:8080"
}

variable "factstore_token" {
  description = "API token for authentication. Overridden by FACTSTORE_API_TOKEN env var. Required: authorisation rules apply even when SECURITY_ENFORCE_AUTH is off, so creating flows, environments and policies needs a credential."
  type        = string
  sensitive   = true
  default     = ""
}

variable "org_slug" {
  description = "URL-safe identifier for the organisation."
  type        = string
  default     = "openfactstore"
}

variable "org_name" {
  description = "Display name for the organisation."
  type        = string
  default     = "OpenFactstore"
}
