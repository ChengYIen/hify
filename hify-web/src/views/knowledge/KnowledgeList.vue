<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, Search, Edit, Delete } from '@element-plus/icons-vue'
import type { FormRules } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import HifyTable, { type Column, type PageParams } from '@/components/common/HifyTable.vue'
import HifyFormDialog, { type FormData } from '@/components/common/HifyFormDialog.vue'
import { useConfirm } from '@/composables/useConfirm'
import { notifySuccess } from '@/utils/notify'
import {
  listKnowledgeBases,
  createKnowledgeBase,
  updateKnowledgeBase,
  deleteKnowledgeBase,
  type KnowledgeBase,
} from '@/api/knowledge'

const router = useRouter()
const tableRef = ref<{ refresh: () => void }>()
const keyword = ref('')

// =========================================================================
// Table
// =========================================================================

const columns: Column[] = [
  { label: '名称', slot: 'name', minWidth: 160 },
  { label: '描述', prop: 'description', minWidth: 240 },
  { label: '状态', slot: 'status', width: 90, align: 'center' },
  { label: '文档数量', prop: 'docCount', width: 100, align: 'center' },
  { label: '创建时间', slot: 'createdAt', width: 140, align: 'center' },
  { label: '操作', slot: 'actions', width: 150, fixed: 'right' },
]

function fetchKnowledgeBases(params: PageParams) {
  return listKnowledgeBases({
    page: params.page,
    size: params.pageSize,
    name: keyword.value || undefined,
  })
}

function handleSearch(): void {
  tableRef.value?.refresh()
}

function handleReset(): void {
  keyword.value = ''
  tableRef.value?.refresh()
}

function formatDate(dateStr: string | null): string {
  if (!dateStr) return '-'
  return dateStr.replace('T', ' ').substring(0, 19)
}

// =========================================================================
// Dialog (create / edit)
// =========================================================================

const dialogVisible = ref(false)
const dialogRef = ref<{ open: (data?: FormData) => void }>()
const editingId = ref<number | null>(null)

const formRules: FormRules = {
  name: [{ required: true, message: '请输入知识库名称', trigger: 'blur' }],
}

function handleCreate(): void {
  editingId.value = null
  dialogRef.value?.open()
}

function handleEdit(row: Record<string, unknown>): void {
  const kb = row as unknown as KnowledgeBase
  editingId.value = kb.id
  dialogRef.value?.open({
    name: kb.name,
    description: kb.description || '',
  })
}

async function handleSubmit(data: FormData): Promise<void> {
  const payload = {
    name: data.name as string,
    description: (data.description as string) || undefined,
  }
  if (editingId.value != null) {
    await updateKnowledgeBase(editingId.value, payload)
    notifySuccess('知识库更新成功')
  } else {
    await createKnowledgeBase(payload)
    notifySuccess('知识库创建成功')
  }
  dialogVisible.value = false
  tableRef.value?.refresh()
}

// =========================================================================
// Delete
// =========================================================================

const { confirmDelete } = useConfirm()

async function handleDelete(row: Record<string, unknown>): Promise<void> {
  const kb = row as unknown as KnowledgeBase
  const ok = await confirmDelete(
    `确定删除知识库「${kb.name}」？关联的文档和向量块会一并删除。`,
    () => deleteKnowledgeBase(kb.id),
  )
  if (ok) {
    tableRef.value?.refresh()
  }
}

// =========================================================================
// Navigation
// =========================================================================

function goDocuments(kb: KnowledgeBase): void {
  router.push(`/knowledge-bases/${kb.id}/documents`)
}
</script>

<template>
  <div class="knowledge-page">
    <PageHeader title="知识库" description="上传和管理文档，构建 RAG 知识库供 Agent 检索">
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="handleCreate">新建知识库</el-button>
      </template>
    </PageHeader>

    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="按名称搜索"
        clearable
        class="toolbar__search"
        @keyup.enter="handleSearch"
        @clear="handleReset"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
    </div>

    <HifyTable ref="tableRef" :columns="columns" :api="fetchKnowledgeBases">
      <template #name="{ row }">
        <el-link type="primary" :underline="false" class="kb-name" @click="goDocuments(row as unknown as KnowledgeBase)">
          {{ (row as unknown as KnowledgeBase).name }}
        </el-link>
      </template>
      <template #status="{ row }">
        <el-tag :type="(row as unknown as KnowledgeBase).enabled === 1 ? 'success' : 'info'">
          {{ (row as unknown as KnowledgeBase).enabled === 1 ? '启用' : '禁用' }}
        </el-tag>
      </template>
      <template #createdAt="{ row }">
        {{ formatDate((row as unknown as KnowledgeBase).createdAt) }}
      </template>
      <template #actions="{ row }">
        <el-button link type="primary" :icon="Edit" @click="handleEdit(row)">编辑</el-button>
        <el-button link type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
      </template>
    </HifyTable>

    <HifyFormDialog
      v-model="dialogVisible"
      ref="dialogRef"
      :title="editingId != null ? '编辑知识库' : '新建知识库'"
      :rules="formRules"
      @submit="handleSubmit"
    >
      <template #default="{ formData }">
        <el-form-item label="名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入知识库名称" maxlength="255" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="formData.description"
            type="textarea"
            :rows="3"
            placeholder="请输入知识库描述（可选）"
            maxlength="512"
          />
        </el-form-item>
      </template>
    </HifyFormDialog>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: var(--hify-section-gap);
}

.toolbar__search {
  width: 280px;
}

.kb-name {
  font-weight: 600;
}
</style>
