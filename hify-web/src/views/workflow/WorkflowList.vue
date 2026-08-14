<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, Delete } from '@element-plus/icons-vue'
import PageHeader from '@/components/common/PageHeader.vue'
import HifyTable, { type Column, type PageParams } from '@/components/common/HifyTable.vue'
import { useConfirm } from '@/composables/useConfirm'
import {
  listWorkflows,
  deleteWorkflow,
  type Workflow,
} from '@/api/workflow'

const router = useRouter()
const tableRef = ref<{ refresh: () => void }>()

const columns: Column[] = [
  { label: '描述', slot: 'description', minWidth: 220 },
  { label: '名称', prop: 'name', minWidth: 180 },
  { label: '状态', slot: 'status', width: 120, align: 'center' },
  { label: '创建时间', slot: 'createdAt', width: 160, align: 'center' },
  { label: '操作', slot: 'actions', width: 100, fixed: 'right' },
]

async function fetchWorkflows(params: PageParams) {
  return listWorkflows({
    page: params.page,
    pageSize: params.pageSize,
  })
}

function statusTagType(status: string): 'success' | 'warning' | 'info' {
  if (status === 'PUBLISHED') return 'success'
  if (status === 'DRAFT') return 'warning'
  return 'info'
}

function formatDate(dateStr: string | null): string {
  if (!dateStr) return '-'
  return dateStr.replace('T', ' ').substring(0, 19)
}

function handleCreate(): void {
  router.push('/workflows/create')
}

const { confirmDelete } = useConfirm()

async function handleDelete(row: Record<string, unknown>): Promise<void> {
  const workflow = row as unknown as Workflow
  const ok = await confirmDelete(
    `确定删除工作流「${workflow.name}」吗？`,
    () => deleteWorkflow(workflow.id),
  )
  if (ok) {
    tableRef.value?.refresh()
  }
}
</script>

<template>
  <div class="workflow-page">
    <PageHeader title="工作流" description="编排多步骤 AI 工作流，支持条件分支和并行执行">
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="handleCreate">新建工作流</el-button>
      </template>
    </PageHeader>

    <HifyTable ref="tableRef" :columns="columns" :api="fetchWorkflows">
      <template #description="{ row }">
        {{ (row as unknown as Workflow).description || '-' }}
      </template>
      <template #status="{ row }">
        <el-tag :type="statusTagType((row as unknown as Workflow).status)">
          {{ (row as unknown as Workflow).status }}
        </el-tag>
      </template>
      <template #createdAt="{ row }">
        {{ formatDate((row as unknown as Workflow).createdAt) }}
      </template>
      <template #actions="{ row }">
        <el-button link type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
      </template>
    </HifyTable>
  </div>
</template>
