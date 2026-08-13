<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Plus, UploadFilled, View, Delete, Loading } from '@element-plus/icons-vue'
import { ElMessage, type UploadInstance, type UploadRequestOptions } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import HifyTable, { type Column, type PageParams } from '@/components/common/HifyTable.vue'
import { useConfirm } from '@/composables/useConfirm'
import { notifySuccess } from '@/utils/notify'
import {
  getKnowledgeBase,
  listDocuments,
  uploadDocument,
  getDocument,
  getDocumentChunks,
  deleteDocument,
  type KnowledgeDocument,
  type DocumentChunk,
} from '@/api/knowledge'

const route = useRoute()
const router = useRouter()
const kbId = Number(route.params.id)
const kbName = ref('')
const tableRef = ref<{ refresh: () => void }>()

// =========================================================================
// Table
// =========================================================================

const columns: Column[] = [
  { label: '文件名', prop: 'filename', minWidth: 180 },
  { label: '文件类型', prop: 'fileType', width: 90, align: 'center' },
  { label: '文件大小', slot: 'fileSize', width: 100, align: 'center' },
  { label: '分块数量', prop: 'chunkCount', width: 100, align: 'center' },
  { label: '处理状态', slot: 'status', width: 120, align: 'center' },
  { label: '创建时间', slot: 'createdAt', width: 140, align: 'center' },
  { label: '操作', slot: 'actions', width: 160, fixed: 'right' },
]

function fetchDocuments(params: PageParams) {
  return listDocuments(kbId, { page: params.page, size: params.pageSize }).then((res) => {
    syncPolling(res.list)
    return res
  })
}

function formatFileSize(size: number): string {
  if (size >= 1024 * 1024) return `${(size / 1024 / 1024).toFixed(1)} MB`
  if (size >= 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${size} B`
}

function formatDate(dateStr: string | null): string {
  if (!dateStr) return '-'
  return dateStr.replace('T', ' ').substring(0, 19)
}

// =========================================================================
// Status
// =========================================================================

const STATUS_LABELS: Record<KnowledgeDocument['status'], string> = {
  PENDING: '等待处理',
  PROCESSING: '处理中',
  DONE: '已完成',
  FAILED: '失败',
}

function statusTagType(status: KnowledgeDocument['status']): 'primary' | 'success' | 'info' | 'danger' {
  const map: Record<KnowledgeDocument['status'], 'primary' | 'success' | 'info' | 'danger'> = {
    PENDING: 'info',
    PROCESSING: 'primary',
    DONE: 'success',
    FAILED: 'danger',
  }
  return map[status]
}

// =========================================================================
// Polling
// =========================================================================

const pollingIds = ref<Set<number>>(new Set())
let pollTimer: number | undefined

function syncPolling(rows: KnowledgeDocument[]): void {
  for (const row of rows) {
    if (row.status === 'PENDING' || row.status === 'PROCESSING') {
      pollingIds.value.add(row.id)
    }
  }
}

async function pollOnce(): Promise<void> {
  const ids = Array.from(pollingIds.value)
  if (ids.length === 0) return
  let finished = false
  for (const id of ids) {
    try {
      const doc = await getDocument(id)
      if (doc.status === 'DONE' || doc.status === 'FAILED') {
        pollingIds.value.delete(id)
        finished = true
      }
    } catch {
      pollingIds.value.delete(id)
    }
  }
  if (finished) {
    tableRef.value?.refresh()
  }
}

onMounted(() => {
  pollTimer = window.setInterval(pollOnce, 3000)
  getKnowledgeBase(kbId)
    .then((kb) => {
      kbName.value = kb.name
    })
    .catch(() => {
      kbName.value = ''
    })
})

onBeforeUnmount(() => {
  if (pollTimer) {
    window.clearInterval(pollTimer)
  }
})

// =========================================================================
// Upload
// =========================================================================

const MAX_FILE_SIZE = 10 * 1024 * 1024
const ALLOWED_TYPES = ['txt', 'md', 'pdf']
const uploadVisible = ref(false)
const uploadLoading = ref(false)
const uploadRef = ref<UploadInstance>()

function beforeUpload(file: File): boolean {
  const ext = (file.name.split('.').pop() || '').toLowerCase()
  if (!ALLOWED_TYPES.includes(ext)) {
    ElMessage.error('仅支持 txt / md / pdf 文件')
    return false
  }
  if (file.size > MAX_FILE_SIZE) {
    ElMessage.error('文件大小不能超过 10MB')
    return false
  }
  return true
}

async function handleUploadRequest(options: UploadRequestOptions): Promise<void> {
  const formData = new FormData()
  formData.append('file', options.file)
  uploadLoading.value = true
  try {
    const documentId = await uploadDocument(kbId, formData)
    notifySuccess('上传成功，正在处理')
    uploadVisible.value = false
    uploadRef.value?.clearFiles()
    pollingIds.value.add(documentId)
    tableRef.value?.refresh()
  } finally {
    uploadLoading.value = false
  }
}

// =========================================================================
// Chunks
// =========================================================================

const chunkVisible = ref(false)
const chunkLoading = ref(false)
const chunkRows = ref<DocumentChunk[]>([])
const expandedChunkIds = ref<Set<number>>(new Set())

async function openChunks(row: Record<string, unknown>): Promise<void> {
  const doc = row as unknown as KnowledgeDocument
  chunkRows.value = []
  expandedChunkIds.value = new Set()
  chunkVisible.value = true
  chunkLoading.value = true
  try {
    chunkRows.value = await getDocumentChunks(doc.id)
  } finally {
    chunkLoading.value = false
  }
}

function toggleChunk(chunkId: number): void {
  const next = new Set(expandedChunkIds.value)
  if (next.has(chunkId)) {
    next.delete(chunkId)
  } else {
    next.add(chunkId)
  }
  expandedChunkIds.value = next
}

function chunkPreview(content: string, expanded: boolean): string {
  if (expanded || content.length <= 200) {
    return content
  }
  return `${content.slice(0, 200)}…`
}

// =========================================================================
// Delete
// =========================================================================

const { confirmDelete } = useConfirm()

async function handleDelete(row: Record<string, unknown>): Promise<void> {
  const doc = row as unknown as KnowledgeDocument
  if (doc.status === 'PROCESSING') return
  const ok = await confirmDelete(`确定删除文档「${doc.filename}」？`, () => deleteDocument(doc.id))
  if (ok) {
    pollingIds.value.delete(doc.id)
    tableRef.value?.refresh()
  }
}

function goBack(): void {
  router.push('/knowledge-bases')
}
</script>

<template>
  <div>
    <PageHeader :title="kbName || '文档管理'" :description="`知识库 ID：${kbId}`">
      <template #actions>
        <el-button :icon="ArrowLeft" @click="goBack">返回</el-button>
        <el-button type="primary" :icon="Plus" @click="uploadVisible = true">上传文档</el-button>
      </template>
    </PageHeader>

    <HifyTable ref="tableRef" :columns="columns" :api="fetchDocuments">
      <template #fileSize="{ row }">
        {{ formatFileSize((row as unknown as KnowledgeDocument).fileSize) }}
      </template>
      <template #status="{ row }">
        <el-tooltip
          :disabled="(row as unknown as KnowledgeDocument).status !== 'FAILED' || !(row as unknown as KnowledgeDocument).errorMessage"
          :content="(row as unknown as KnowledgeDocument).errorMessage || ''"
          placement="top"
        >
          <el-tag :type="statusTagType((row as unknown as KnowledgeDocument).status)">
            <el-icon v-if="(row as unknown as KnowledgeDocument).status === 'PROCESSING'" class="is-loading">
              <Loading />
            </el-icon>
            <span class="status-label">{{ STATUS_LABELS[(row as unknown as KnowledgeDocument).status] }}</span>
          </el-tag>
        </el-tooltip>
      </template>
      <template #createdAt="{ row }">
        {{ formatDate((row as unknown as KnowledgeDocument).createdAt) }}
      </template>
      <template #actions="{ row }">
        <el-button link type="primary" :icon="View" @click="openChunks(row)">查看分块</el-button>
        <el-button
          link
          type="danger"
          :icon="Delete"
          :disabled="(row as unknown as KnowledgeDocument).status === 'PROCESSING'"
          @click="handleDelete(row)"
        >
          删除
        </el-button>
      </template>
    </HifyTable>

    <el-dialog v-model="uploadVisible" title="上传文档" width="560px" :close-on-click-modal="false">
      <el-upload
        ref="uploadRef"
        drag
        accept=".txt,.md,.pdf"
        :limit="1"
        :multiple="false"
        :disabled="uploadLoading"
        :before-upload="beforeUpload"
        :http-request="handleUploadRequest"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽文件到此处，或<em>点击选择文件</em></div>
        <template #tip>
          <div class="el-upload__tip">支持 txt / md / pdf，单个文件不超过 10MB</div>
        </template>
      </el-upload>
      <template #footer>
        <el-button :disabled="uploadLoading" @click="uploadVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="chunkVisible" title="文档分块" width="760px">
      <el-table :data="chunkRows" v-loading="chunkLoading" border stripe max-height="480">
        <template #empty>
          <el-empty description="该文档暂无分块" />
        </template>
        <el-table-column label="序号" width="70" align="center">
          <template #default="{ row }">
            {{ (row as unknown as DocumentChunk).chunkIndex + 1 }}
          </template>
        </el-table-column>
        <el-table-column label="内容">
          <template #default="{ row }">
            <div class="chunk-cell">
              <p class="chunk-cell__content">
                {{ chunkPreview((row as unknown as DocumentChunk).content, expandedChunkIds.has((row as unknown as DocumentChunk).id)) }}
              </p>
              <el-button
                v-if="(row as unknown as DocumentChunk).content.length > 200"
                link
                type="primary"
                @click="toggleChunk((row as unknown as DocumentChunk).id)"
              >
                {{ expandedChunkIds.has((row as unknown as DocumentChunk).id) ? '收起' : '展开全文' }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="chunkVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.status-label {
  margin-left: 4px;
}

.chunk-cell {
  padding: 4px 0;
}

.chunk-cell__content {
  margin: 0 0 6px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
