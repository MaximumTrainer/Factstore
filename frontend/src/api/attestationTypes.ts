import client from './client'

export interface CustomAttestationType {
  id: string
  name: string
  description: string
  version: number
  orgSlug?: string
  archivedAt?: string
  createdAt: string
  updatedAt: string
}

export interface CreateCustomAttestationTypeRequest {
  name: string
  description: string
  orgSlug?: string
}

export interface UpdateCustomAttestationTypeRequest {
  description?: string
  orgSlug?: string
}

export function listAttestationTypes(includeArchived = false) {
  return client.get<CustomAttestationType[]>(`/attestation-types?includeArchived=${includeArchived}`)
}

export function createAttestationType(req: CreateCustomAttestationTypeRequest) {
  return client.post<CustomAttestationType>('/attestation-types', req)
}

export function updateAttestationType(id: string, req: UpdateCustomAttestationTypeRequest) {
  return client.put<CustomAttestationType>(`/attestation-types/${id}`, req)
}

export function archiveAttestationType(id: string) {
  return client.post<CustomAttestationType>(`/attestation-types/${id}/archive`, {})
}

export function unarchiveAttestationType(id: string) {
  return client.post<CustomAttestationType>(`/attestation-types/${id}/unarchive`, {})
}
