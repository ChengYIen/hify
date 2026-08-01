import { get } from '@/utils/request'

export interface Workflow {
  id: number
  name: string
  status: 'idle' | 'running' | 'completed' | 'failed'
  createdAt: string
}

export function listWorkflows() {
  return get<Workflow[]>('/v1/workflows')
}

export function getWorkflow(id: number) {
  return get<Workflow>(`/v1/workflows/${id}`)
}
