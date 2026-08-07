<script setup lang="ts">
import { ref } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import type { FormRules } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import HifyTable, { type Column, type PageParams } from '@/components/common/HifyTable.vue'
import HifyFormDialog, { type FormData } from '@/components/common/HifyFormDialog.vue'
import { useConfirm } from '@/composables/useConfirm'
import { notifySuccess } from '@/utils/notify'
import {
  listAgents,
  createAgent,
  updateAgent,
  deleteAgent,
  type AgentListResponse,
  type AgentCreateData,
  type AgentUpdateData,
} from '@/api/agent'

// =========================================================================
// Table
// =========================================================================

const tableRef = ref<{ refresh: () => void }>()

const columns: Column[] = [
  { label: '名称', prop: 'name', minWidth: 140 },
  { label: '描述', prop: 'description', minWidth: 180 },
  { label: '温度', prop: 'temperature', width: 80, align: 'center' },
  { label: '工具数', slot: 'toolCount', width: 80, align: 'center' },
  { label: '状态', slot: 'status', width: 90, align: 'center' },
  { label: '创建时间', slot: 'createdAt', width: 110, align: 'center' },
  { label: '操作', slot: 'actions', width: 160, fixed: 'right' },
]

async function fetchAgents(params: PageParams) {
  return listAgents({ page: params.page, pageSize: params.pageSize })
}

// =========================================================================
// Helpers
// =========================================================================

const STATUS_LABELS: Record<string, string> = {
  ENABLED: '启用',
  DISABLED: '禁用',
  DRAFT: '草稿',
}

function getStatusTagType(status: string): 'success' | 'info' | 'warning' {
  const map: Record<string, 'success' | 'info' | 'warning'> = {
    ENABLED: 'success',
    DISABLED: 'info',
    DRAFT: 'warning',
  }
  return map[status] || 'info'
}

function formatDate(dateStr: unknown): string {
  if (!dateStr || typeof dateStr !== 'string') return '-'
  return dateStr.substring(0, 10)
}

// =========================================================================
// Dialog (create / edit)
// =========================================================================

const dialogVisible = ref(false)
const dialogRef = ref<{ open: (data?: Record<string, unknown>) => void; isEdit: boolean }>()
const editingId = ref<number | null>(null)

const formRules: FormRules = {
  name: [{ required: true, message: '请输入 Agent 名称', trigger: 'blur' }],
  systemPrompt: [{ required: true, message: '请输入系统提示词', trigger: 'blur' }],
  modelConfigId: [{ required: true, message: '请输入模型配置 ID', trigger: 'blur' }],
}

function handleCreate(): void {
  editingId.value = null
  dialogRef.value?.open()
}

function handleEdit(row: Record<string, unknown>): void {
  const a = row as unknown as AgentListResponse
  editingId.value = a.id
  dialogRef.value?.open({
    name: a.name,
    description: a.description || '',
    systemPrompt: a.systemPrompt,
    modelConfigId: a.modelConfigId,
    temperature: a.temperature,
    maxTokens: a.maxTokens,
    maxContextTurns: a.maxContextTurns,
    toolsEnabled: a.toolsEnabled,
    knowledgeIds: a.knowledgeIds || '',
    status: a.status,
  })
}

async function handleSubmit(data: FormData): Promise<void> {
  if (editingId.value) {
    const updateData: AgentUpdateData = {
      name: data.name as string,
      description: (data.description as string) || undefined,
      systemPrompt: data.systemPrompt as string | undefined,
      modelId: data.modelConfigId != null ? Number(data.modelConfigId) : undefined,
      temperature: data.temperature != null ? Number(data.temperature) : undefined,
      maxTokens: data.maxTokens != null ? Number(data.maxTokens) : undefined,
      maxIterations: data.maxContextTurns != null ? Number(data.maxContextTurns) : undefined,
      toolsEnabled: data.toolsEnabled != null ? Number(data.toolsEnabled) : undefined,
      knowledgeIds: (data.knowledgeIds as string) || undefined,
      status: data.status as string | undefined,
    }
    await updateAgent(editingId.value, updateData)
    notifySuccess('更新成功')
  } else {
    const createData: AgentCreateData = {
      name: data.name as string,
      description: (data.description as string) || undefined,
      systemPrompt: data.systemPrompt as string,
      modelConfigId: Number(data.modelConfigId),
      temperature: data.temperature != null ? Number(data.temperature) : undefined,
      maxTokens: data.maxTokens != null ? Number(data.maxTokens) : undefined,
      maxContextTurns: data.maxContextTurns != null ? Number(data.maxContextTurns) : undefined,
      toolsEnabled: data.toolsEnabled != null ? Number(data.toolsEnabled) : undefined,
      knowledgeIds: (data.knowledgeIds as string) || undefined,
      status: (data.status as string) || undefined,
    }
    await createAgent(createData)
    notifySuccess('创建成功')
  }

  dialogVisible.value = false
  tableRef.value?.refresh()
}

// =========================================================================
// Delete
// =========================================================================

const { confirmDelete } = useConfirm()

async function handleDelete(row: Record<string, unknown>): Promise<void> {
  const a = row as unknown as AgentListResponse
  const ok = await confirmDelete(`确定删除 Agent「${a.name}」？`, () => deleteAgent(a.id))
  if (ok) {
    tableRef.value?.refresh()
  }
}
</script>

<template>
  <PageHeader
    title="Agent 管理"
    description="创建和管理 AI Agent，配置系统提示词、工具集和知识库"
  >
    <template #actions>
      <el-button type="primary" :icon="Plus" @click="handleCreate">创建 Agent</el-button>
    </template>
  </PageHeader>

  <HifyTable
    ref="tableRef"
    :columns="columns"
    :api="fetchAgents"
  >
    <!-- 工具数 -->
    <template #toolCount="{ row }">
      <span>{{ row.toolCount ?? 0 }}</span>
    </template>

    <!-- 状态 -->
    <template #status="{ row }">
      <el-tag
        :type="getStatusTagType(row.status)"
        size="small"
        effect="light"
      >
        {{ STATUS_LABELS[row.status] || row.status }}
      </el-tag>
    </template>

    <!-- 创建时间 -->
    <template #createdAt="{ row }">
      {{ formatDate(row.createdAt) }}
    </template>

    <!-- 操作 -->
    <template #actions="{ row }">
      <div class="action-btns">
        <el-button type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
        <el-button type="danger" text size="small" @click="handleDelete(row)">删除</el-button>
      </div>
    </template>
  </HifyTable>

  <HifyFormDialog
    ref="dialogRef"
    v-model="dialogVisible"
    :title="editingId ? '编辑 Agent' : '创建 Agent'"
    :rules="formRules"
    label-width="120px"
    width="640px"
    @submit="handleSubmit"
  >
    <template #default="{ formData }">
      <el-form-item label="名称" prop="name">
        <el-input v-model="formData.name" placeholder="例如：客服助手" maxlength="128" show-word-limit />
      </el-form-item>
      <el-form-item label="描述" prop="description">
        <el-input v-model="formData.description" placeholder="可选：简要描述 Agent 的用途" maxlength="512" show-word-limit />
      </el-form-item>
      <el-form-item label="系统提示词" prop="systemPrompt">
        <el-input
          v-model="formData.systemPrompt"
          type="textarea"
          :rows="6"
          placeholder="定义 Agent 的角色、行为和约束..."
        />
      </el-form-item>
      <el-form-item label="模型配置 ID" prop="modelConfigId">
        <el-input-number v-model="formData.modelConfigId" :min="1" placeholder="关联的模型配置 ID" style="width: 100%" />
      </el-form-item>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="温度" prop="temperature">
            <el-input-number
              v-model="formData.temperature"
              :min="0"
              :max="2"
              :step="0.1"
              :precision="2"
              placeholder="0.00–2.00"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="上下文轮次" prop="maxContextTurns">
            <el-input-number v-model="formData.maxContextTurns" :min="1" :max="50" placeholder="默认 10" style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="最大 Token" prop="maxTokens">
            <el-input-number v-model="formData.maxTokens" :min="1" :step="256" placeholder="默认 4096" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="启用工具" prop="toolsEnabled">
            <el-switch v-model="formData.toolsEnabled" :active-value="1" :inactive-value="0" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="知识库 IDs" prop="knowledgeIds">
        <el-input v-model="formData.knowledgeIds" placeholder="可选：JSON 数组，如 [1, 2, 3]" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="formData.status" placeholder="请选择状态" style="width: 100%">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="启用" value="ENABLED" />
          <el-option label="禁用" value="DISABLED" />
        </el-select>
      </el-form-item>
    </template>
  </HifyFormDialog>
</template>

<style scoped>
.action-btns {
  display: flex;
  align-items: center;
  gap: var(--hify-space-2);
}
</style>
