import { get } from '@/utils/request'

export interface Message {
  id: number
  role: 'user' | 'assistant'
  content: string
  createdAt: string
}

export interface Conversation {
  id: number
  title: string
  messages: Message[]
  createdAt: string
}

export function listConversations() {
  return get<Conversation[]>('/v1/conversations')
}

export function getConversation(id: number) {
  return get<Conversation>(`/v1/conversations/${id}`)
}
