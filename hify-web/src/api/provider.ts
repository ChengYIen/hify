import { get } from '@/utils/request'

export interface Provider {
  id: number
  name: string
  type: string
  apiBaseUrl: string
  enabled: boolean
  createdAt: string
}

export function listProviders() {
  return get<Provider[]>('/v1/providers')
}

export function getProvider(id: number) {
  return get<Provider>(`/v1/providers/${id}`)
}
