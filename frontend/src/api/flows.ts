import client from './client'
import type { Flow, FlowImpact } from '../types'

export const getFlows = (includeArchived = false) =>
  client.get<Flow[]>(`/flows?includeArchived=${includeArchived}`)

export const getFlow = (id: string) => client.get<Flow>(`/flows/${id}`)

export const createFlow = (data: {
  name: string
  description: string
  requiredAttestationTypes: string[]
  tags?: Record<string, string>
  templateYaml?: string
  requiresApproval?: boolean
  requiredApproverRoles?: string[]
}) => client.post<Flow>('/flows', data)

/**
 * Updates a flow definition. Every field is optional and only what is sent is changed.
 *
 * The fields here mirror the server's `UpdateFlowRequest` exactly. An earlier version omitted
 * `templateYaml`, `requiresApproval` and `requiredApproverRoles` — so they could not be edited at
 * all — and sent a `visibility` field the backend has never had, which was silently discarded.
 */
export interface UpdateFlowPayload {
  name?: string
  description?: string
  requiredAttestationTypes?: string[]
  tags?: Record<string, string>
  templateYaml?: string
  requiresApproval?: boolean
  requiredApproverRoles?: string[]
}

export const updateFlow = (id: string, data: UpdateFlowPayload) =>
  client.put<Flow>(`/flows/${id}`, data)

/** How much existing evidence a change to this flow would affect. */
export const getFlowImpact = (id: string) => client.get<FlowImpact>(`/flows/${id}/impact`)

export const renameFlow = (id: string, newName: string) =>
  client.post<Flow>(`/flows/${id}/rename`, { newName })

export const archiveFlow = (id: string) => client.post<Flow>(`/flows/${id}/archive`, {})

export const unarchiveFlow = (id: string) => client.post<Flow>(`/flows/${id}/unarchive`, {})
