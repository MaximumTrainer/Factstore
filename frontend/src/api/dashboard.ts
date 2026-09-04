import client from './client'
import type { DashboardStats, DeliveryMetrics } from '../types'

export const getDashboardStats = () => client.get<DashboardStats>('/dashboard/stats')

/** DORA-where-derivable plus gate and assertion metrics over a rolling window (#151). */
export const getDeliveryMetrics = (days: number) =>
  client.get<DeliveryMetrics>('/metrics/delivery', { params: { days } })
