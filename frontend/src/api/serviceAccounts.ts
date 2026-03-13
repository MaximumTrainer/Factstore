import client from './client'
import type {
  ServiceAccount,
  CreateServiceAccountRequest,
  UpdateServiceAccountRequest,
  ApiKey,
  ApiKeyCreated,
} from '../types'

// Service account endpoints
export const getServiceAccounts = () =>
  client.get<ServiceAccount[]>('/service-accounts')

export const getServiceAccount = (id: string) =>
  client.get<ServiceAccount>(`/service-accounts/${id}`)

export const createServiceAccount = (data: CreateServiceAccountRequest) =>
  client.post<ServiceAccount>('/service-accounts', data)

export const updateServiceAccount = (id: string, data: UpdateServiceAccountRequest) =>
  client.put<ServiceAccount>(`/service-accounts/${id}`, data)

export const deleteServiceAccount = (id: string) =>
  client.delete(`/service-accounts/${id}`)

// API key sub-resource for service accounts
export const getServiceAccountApiKeys = (serviceAccountId: string) =>
  client.get<ApiKey[]>(`/service-accounts/${serviceAccountId}/api-keys`)

/**
 * Generates a new API key for a service account.
 * The plain-text key is returned **exactly once** — store it securely.
 */
export const createServiceAccountApiKey = (
  serviceAccountId: string,
  label: string,
  ttlDays?: number
) =>
  client.post<ApiKeyCreated>(`/service-accounts/${serviceAccountId}/api-keys`, { label, ttlDays })

export const revokeServiceAccountApiKey = (serviceAccountId: string, keyId: string) =>
  client.delete(`/service-accounts/${serviceAccountId}/api-keys/${keyId}`)
