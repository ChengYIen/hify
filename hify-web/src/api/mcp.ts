import { del, get, post, put } from '@/utils/request'

export interface McpTool {
  id: number
  mcpServerId: number
  toolName: string
  description: string | null
  inputSchema: string | null
  createdAt: string
  updatedAt: string
}

export function listMcpTools() {
  return get<McpTool[]>('/v1/mcp/tools')
}

export function getMcpTool(id: number) {
  return get<McpTool>(`/v1/mcp/tools/${id}`)
}

export type McpServerListResponse = {
  id: number
  name: string
  description: string | null
  endpoint: string | null
  enabled: boolean | null
  toolCount: number
  createdAt: string
  updatedAt: string
}

export type McpServerDetailResponse = McpServerListResponse & {
  tools: McpTool[]
}

export interface McpDebugResponse {
  result: string
  elapsedMs: number
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

export type McpServerPageResult = {
  list: McpServerListResponse[]
  total: number
}

export function listMcpServers(params: { page: number; pageSize: number }): Promise<McpServerPageResult> {
  return get<RawPageResult<McpServerListResponse>>('/v1/mcp-servers', { params }).then(
    (res) => ({ list: res.data, total: res.total }),
  )
}

export function getMcpServer(id: number): Promise<McpServerDetailResponse> {
  return get<McpServerDetailResponse>(`/v1/mcp-servers/${id}`)
}

export type McpServerCreatePayload = {
  name: string
  endpoint: string
  description?: string
  enabled?: boolean
}

export function createMcpServer(payload: McpServerCreatePayload): Promise<McpServerDetailResponse> {
  return post<McpServerDetailResponse>('/v1/mcp-servers', payload)
}

export function updateMcpServer(
  id: number,
  payload: McpServerCreatePayload,
): Promise<McpServerDetailResponse> {
  return put<McpServerDetailResponse>(`/v1/mcp-servers/${id}`, payload)
}

export function deleteMcpServer(id: number): Promise<void> {
  return del<void>(`/v1/mcp-servers/${id}`)
}

export type McpConnectionTestResult = {
  success: boolean
  latencyMs: number
  toolCount: number
  errorMessage: string | null
}

export function testMcpServer(id: number): Promise<McpConnectionTestResult> {
  return post<McpConnectionTestResult>(`/v1/mcp-servers/${id}/test`)
}

export function debugMcpTool(
  id: number,
  toolName: string,
  args: Record<string, unknown>,
): Promise<McpDebugResponse> {
  return post<McpDebugResponse>(`/v1/mcp-servers/${id}/debug`, { toolName, arguments: args })
}
