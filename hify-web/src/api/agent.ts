import { get, post, put, del } from '@/utils/request'

// =========================================================================
// Types
// =========================================================================

export type AgentToolData = {
  toolName: string
  toolType: string
  toolConfig?: string
  priority?: number
}

export type AgentToolResponse = AgentToolData & {
  id: number
  agentId: number
  createdAt: string
  updatedAt: string
}

export type AgentListResponse = {
  id: number
  name: string
  description: string | null
  avatarUrl: string | null
  systemPrompt: string
  modelConfigId: number
  temperature: number | null
  maxTokens: number | null
  maxContextTurns: number
  toolsEnabled: number
  knowledgeIds: string | null
  status: string
  createdBy: number | null
  toolCount: number
  createdAt: string
  updatedAt: string
}

export type AgentDetailResponse = AgentListResponse & {
  tools: AgentToolResponse[]
}

export type AgentPageResult = {
  list: AgentListResponse[]
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

export type AgentCreateData = {
  name: string
  description?: string
  avatarUrl?: string
  systemPrompt: string
  modelConfigId: number
  temperature?: number
  maxTokens?: number
  maxContextTurns?: number
  toolsEnabled?: number
  knowledgeIds?: string
  status?: string
  /** 绑定的工具定义 ID 列表（引用 hify_tool_definition.id） */
  toolIds?: number[]
}

export type AgentUpdateData = {
  name?: string
  description?: string
  avatarUrl?: string
  systemPrompt?: string
  modelId?: number
  temperature?: number
  maxTokens?: number
  maxIterations?: number
  toolsEnabled?: number
  knowledgeIds?: string
  status?: string
}

// =========================================================================
// API Functions
// =========================================================================

/** 分页列表 */
export function listAgents(params: { page: number; pageSize: number }): Promise<AgentPageResult> {
  return get<RawPageResult<AgentListResponse>>('/v1/agents', { params }).then(
    (res) => ({ list: res.data, total: res.total }),
  )
}

/** 详情（含工具列表） */
export function getAgent(id: number): Promise<AgentDetailResponse> {
  return get<AgentDetailResponse>(`/v1/agents/${id}`)
}

/** 创建 */
export function createAgent(data: AgentCreateData): Promise<AgentDetailResponse> {
  return post<AgentDetailResponse>('/v1/agents', data)
}

/** 更新基本信息 */
export function updateAgent(id: number, data: AgentUpdateData): Promise<AgentDetailResponse> {
  return put<AgentDetailResponse>(`/v1/agents/${id}`, data)
}

/** 更新工具列表（全量替换） */
export function updateAgentTools(id: number, tools: AgentToolData[]): Promise<AgentToolData[]> {
  return put<AgentToolData[]>(`/v1/agents/${id}/tools`, tools)
}

/** 删除 */
export function deleteAgent(id: number): Promise<void> {
  return del<void>(`/v1/agents/${id}`)
}
