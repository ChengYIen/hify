import { get } from '@/utils/request'

export interface KnowledgeBase {
  id: number
  name: string
  description: string
  documentCount: number
  createdAt: string
}

export function listKnowledgeBases() {
  return get<KnowledgeBase[]>('/v1/knowledge')
}

export function getKnowledgeBase(id: number) {
  return get<KnowledgeBase>(`/v1/knowledge/${id}`)
}
