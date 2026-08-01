import { get } from '@/utils/request'

export interface AgentConfig {
  id: number
  name: string
  model: string
  systemPrompt: string
  maxIterations: number
  createdAt: string
}

export function listAgents() {
  return get<AgentConfig[]>('/v1/agents')
}

export function getAgent(id: number) {
  return get<AgentConfig>(`/v1/agents/${id}`)
}
