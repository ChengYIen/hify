import { get } from '@/utils/request'

export interface McpTool {
  id: number
  name: string
  description: string
  enabled: boolean
  createdAt: string
}

export function listMcpTools() {
  return get<McpTool[]>('/v1/mcp/tools')
}

export function getMcpTool(id: number) {
  return get<McpTool>(`/v1/mcp/tools/${id}`)
}
