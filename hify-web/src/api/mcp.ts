import { get } from '@/utils/request'

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
