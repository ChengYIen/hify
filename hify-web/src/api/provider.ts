import { get, post, put, del } from '@/utils/request'

// =========================================================================
// Types（使用 type 以兼容 HifyTable 泛型 Record<string, unknown> 约束）
// =========================================================================

export type AuthConfig = {
  apiKey?: string
  apiKeyId?: string
  anthropicVersion?: string
}

export type ProviderModelResponse = {
  id: number
  providerId: number
  modelName: string
  displayName: string | null
  modelType: string
  contextWindow: number
  maxOutput: number
  supportsVision: number
  supportsTools: number
  supportsStreaming: number
  priority: number
  fallbackModelId: number | null
  status: string
  createdAt: string
  updatedAt: string
}

export type ProviderResponse = {
  id: number
  name: string
  description: string | null
  providerCode: string
  baseUrl: string | null
  status: string
  healthStatus: string
  discoveryType: string | null
  priority: number
  lastSyncedAt: string | null
  createdAt: string
  updatedAt: string
  /** 已启用模型数量（列表接口填充） */
  modelCount: number
  /** 最近一次健康检查响应时间 ms */
  lastHealthResponseTimeMs: number | null
  /** 模型配置列表（仅详情接口填充） */
  modelConfigs?: ProviderModelResponse[]
}

export type ConnectionTestResult = {
  success: boolean
  latencyMs: number
  modelCount: number
  errorMessage: string | null
}

export type ProviderListParams = {
  page: number
  pageSize: number
  providerCode?: string
  status?: string
}

export type ProviderPageResult = {
  list: ProviderResponse[]
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

export type ProviderCreateData = {
  name: string
  description?: string
  providerCode: string
  authConfig?: AuthConfig
  baseUrl?: string
  discoveryType?: string
  priority?: number
}

export type ProviderUpdateData = {
  name?: string
  description?: string
  authConfig?: AuthConfig
  baseUrl?: string
  status?: string
  priority?: number
}

// =========================================================================
// API Functions
// =========================================================================

/** 分页列表（后端返回 PageResult code=0，需手动解包 data） */
export function getProviderList(params: ProviderListParams): Promise<ProviderPageResult> {
  return get<RawPageResult<ProviderResponse>>('/v1/providers', { params }).then(
    (res) => ({ list: res.data, total: res.total }),
  )
}

/** 详情（含 modelConfigs 和 latestHealth） */
export function getProvider(id: number): Promise<ProviderResponse> {
  return get<ProviderResponse>(`/v1/providers/${id}`)
}

/** 创建 */
export function createProvider(data: ProviderCreateData): Promise<ProviderResponse> {
  return post<ProviderResponse>('/v1/providers', data)
}

/** 更新 */
export function updateProvider(id: number, data: ProviderUpdateData): Promise<ProviderResponse> {
  return put<ProviderResponse>(`/v1/providers/${id}`, data)
}

/** 删除 */
export function deleteProvider(id: number): Promise<void> {
  return del<void>(`/v1/providers/${id}`)
}

/** 连通性测试 */
export function testConnection(id: number): Promise<ConnectionTestResult> {
  return post<ConnectionTestResult>(`/v1/providers/${id}/test-connection`)
}

/** 获取某 provider 下的所有模型 */
export function getProviderModels(providerId: number): Promise<ProviderModelResponse[]> {
  return get<ProviderModelResponse[]>(`/v1/providers/${providerId}/models`)
}
