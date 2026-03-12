import client from './client'
import type { WebhookConfig, WebhookDelivery } from '../types'

export const getWebhookConfigs = () => client.get<WebhookConfig[]>('/webhook-configs')

export const createWebhookConfig = (data: { source: string; secret: string; flowId: string }) =>
  client.post<WebhookConfig>('/webhook-configs', data)

export const deleteWebhookConfig = (id: string) => client.delete(`/webhook-configs/${id}`)

export const getWebhookDeliveries = (configId: string) =>
  client.get<WebhookDelivery[]>(`/webhook-configs/${configId}/deliveries`)

export const sendTestWebhook = (source: string, payload: object) =>
  client.post(`/webhooks/${source}`, payload)
