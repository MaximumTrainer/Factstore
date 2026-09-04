import client from './client'

export type TemplateCategory = 'SERVICE_TYPE' | 'FRAMEWORK'

export interface HubTemplate {
  id: string
  name: string
  description: string
  framework: string
  version: string
  yaml: string
  category: TemplateCategory
  /** Set on service-type templates: the shape of service this is the baseline for. */
  serviceType?: string
  /** Null for built-ins; set for templates an organisation has published itself. */
  orgSlug?: string | null
}

export interface ComposedTemplate {
  templateIds: string[]
  templateYaml: string
  requiredAttestations: string[]
  /** Gates the chosen templates disagree about, stated rather than silently resolved. */
  conflicts: string[]
}

export interface OrgTemplate extends HubTemplate {
  templateId: string
  templateYaml: string
  createdAt: string
  updatedAt: string
}

export const listTemplates = (category?: TemplateCategory, orgSlug?: string) =>
  client.get<HubTemplate[]>('/hub/templates', {
    params: { ...(category ? { category } : {}), ...(orgSlug ? { orgSlug } : {}) },
  })

export const getTemplate = (id: string) => client.get<HubTemplate>(`/hub/templates/${id}`)

/** Merges several templates — a service type plus a regulatory framework, typically. */
export const composeTemplates = (templateIds: string[], orgSlug?: string) =>
  client.post<ComposedTemplate>('/hub/templates/compose', { templateIds, orgSlug })

export const listOrgTemplates = (orgSlug?: string) =>
  client.get<OrgTemplate[]>('/hub/templates/custom', { params: orgSlug ? { orgSlug } : {} })

export const createOrgTemplate = (data: {
  templateId: string
  name: string
  description?: string
  templateYaml: string
  category?: TemplateCategory
  serviceType?: string
  framework?: string
  version?: string
  orgSlug?: string
}) => client.post<OrgTemplate>('/hub/templates/custom', data)

export const deleteOrgTemplate = (id: string) => client.delete<void>(`/hub/templates/custom/${id}`)
