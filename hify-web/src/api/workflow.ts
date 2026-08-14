import { get, post, del } from '@/utils/request'

export type WorkflowNodeType = 'START' | 'END' | 'LLM' | 'CONDITION' | 'API_CALL' | 'KNOWLEDGE'

export interface WorkflowNode {
  nodeKey: string
  name?: string
  type: WorkflowNodeType
  config: Record<string, unknown>
}

export interface WorkflowEdge {
  sourceNodeKey: string
  targetNodeKey: string
  condition?: string | null
}

export type Workflow = {
  id: number
  name: string
  description: string | null
  status: 'DRAFT' | 'PUBLISHED' | 'DISABLED' | string
  version: number
  createdBy: number | null
  createdAt: string
  updatedAt: string
  nodes?: WorkflowNode[]
  edges?: WorkflowEdge[]
}

export interface WorkflowCreateData {
  name: string
  description?: string
  nodes: WorkflowNode[]
  edges: WorkflowEdge[]
}

export interface WorkflowListResult {
  list: Workflow[]
  total: number
}

/** 后端 PageResult 原始响应（code=0，拦截器不解包） */
type RawPageResult<T> = {
  code: number
  message: string
  data: T[]
  total: number
  page: number
  size: number
}

export function listWorkflows(params: {
  page: number
  pageSize: number
}): Promise<WorkflowListResult> {
  return get<RawPageResult<Workflow>>('/v1/workflows', { params }).then(
    (res) => ({ list: res.data, total: res.total }),
  )
}

export function getWorkflow(id: number) {
  return get<Workflow>(`/v1/workflows/${id}`)
}

export function createWorkflow(data: WorkflowCreateData) {
  return post<Workflow>('/v1/workflows', data)
}

export function deleteWorkflow(id: number) {
  return del<void>(`/v1/workflows/${id}`)
}
