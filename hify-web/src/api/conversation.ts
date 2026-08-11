import { get, post } from '@/utils/request'
import type { PageResult } from '@/types'

export interface SessionResponse {
  id: number
  title: string
  userId: number
  agentId: number | null
  modelId: number
  status: string
  messageCount: number
  totalTokens: number
  createdAt: string
  updatedAt: string
}

export interface MessageResponse {
  id: number
  sessionId: number
  role: 'user' | 'assistant'
  content: string
  model: string | null
  finishReason: string | null
  latencyMs: number | null
  toolCalls: string | null
  toolCallId: string | null
  fallback: number | null
  seq: number | null
  createdAt: string
}

/**
 * 流式接口是 POST（需携带请求体，EventSource 不支持，必须 fetch 手动解析）.
 * 返回 fetch 用的完整 URL：已有会话走会话内消息端点，否则走无会话直发（服务端自动建会话）。
 */
export function chatStreamUrl(sessionId: number | null): string {
  return sessionId != null
    ? `/api/v1/chat/sessions/${sessionId}/messages`
    : '/api/v1/chat/messages'
}

/** 会话分页（按 updatedAt 倒序） */
export function listSessions(page = 1, pageSize = 20) {
  return get<PageResult<SessionResponse>>('/v1/chat/sessions', { params: { page, pageSize } })
}

export function getSession(id: number) {
  return get<SessionResponse>(`/v1/chat/sessions/${id}`)
}

/** 会话内消息分页 */
export function listMessages(sessionId: number, page = 1, pageSize = 50) {
  return get<PageResult<MessageResponse>>(`/v1/chat/sessions/${sessionId}/messages`, {
    params: { page, pageSize },
  })
}

/** 会话内最近 N 条消息（时间正序） */
export function latestMessages(sessionId: number, limit = 20) {
  return get<MessageResponse[]>(`/v1/chat/sessions/${sessionId}/messages/latest`, {
    params: { limit },
  })
}

/** 同步阻塞发送（stream=false），返回完整助手消息 */
export function sendMessageBlocking(sessionId: number | null, content: string) {
  return post<MessageResponse>(
    sessionId != null ? `/v1/chat/sessions/${sessionId}/messages` : '/v1/chat/messages',
    { content, stream: false },
  )
}
