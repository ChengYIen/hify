import { get, post, put, del } from '@/utils/request'

export type KnowledgeBase = {
  id: number
  name: string
  description: string | null
  enabled: number
  docCount: number
  chunkCount: number
  embeddingModelId: number | null
  createdBy: number | null
  createdAt: string
  updatedAt: string
}

export type KnowledgeBaseListResult = {
  list: KnowledgeBase[]
  total: number
}

export interface KnowledgeBaseSaveData {
  name: string
  description?: string
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

export function listKnowledgeBases(params: {
  page: number
  size: number
  name?: string
}): Promise<KnowledgeBaseListResult> {
  return get<RawPageResult<KnowledgeBase>>('/v1/knowledge-bases', { params }).then(
    (res) => ({ list: res.data, total: res.total }),
  )
}

export function createKnowledgeBase(data: KnowledgeBaseSaveData) {
  return post<KnowledgeBase>('/v1/knowledge-bases', data)
}

export function updateKnowledgeBase(id: number, data: KnowledgeBaseSaveData) {
  return put<KnowledgeBase>(`/v1/knowledge-bases/${id}`, data)
}

export function deleteKnowledgeBase(id: number) {
  return del<void>(`/v1/knowledge-bases/${id}`)
}

export function getKnowledgeBase(id: number) {
  return get<KnowledgeBase>(`/v1/knowledge-bases/${id}`)
}

// =========================================================================
// Documents
// =========================================================================

export type KnowledgeDocument = {
  id: number
  knowledgeId: number
  filename: string
  fileType: string
  fileSize: number
  fileUrl: string | null
  status: 'PENDING' | 'PROCESSING' | 'DONE' | 'FAILED'
  chunkCount: number
  errorMessage: string | null
  createdAt: string
  updatedAt: string
}

export type DocumentChunk = {
  id: number
  knowledgeId: number
  documentId: number
  chunkIndex: number
  content: string
  createdAt: string
}

export type DocumentListResult = {
  list: KnowledgeDocument[]
  total: number
}

export function listDocuments(kbId: number, params: {
  page: number
  size: number
}): Promise<DocumentListResult> {
  return get<RawPageResult<KnowledgeDocument>>(`/v1/knowledge-bases/${kbId}/documents`, { params }).then(
    (res) => ({ list: res.data, total: res.total }),
  )
}

export function uploadDocument(kbId: number, formData: FormData): Promise<number> {
  return post<number>(`/v1/knowledge-bases/${kbId}/documents`, formData)
}

export function getDocument(id: number) {
  return get<KnowledgeDocument>(`/v1/documents/${id}`)
}

export function getDocumentChunks(id: number) {
  return get<DocumentChunk[]>(`/v1/documents/${id}/chunks`)
}

export function deleteDocument(id: number) {
  return del<void>(`/v1/documents/${id}`)
}
