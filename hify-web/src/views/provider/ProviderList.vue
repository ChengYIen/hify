<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { FormRules } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import HifyTable, { type Column, type PageParams } from '@/components/common/HifyTable.vue'
import HifyFormDialog, { type FormData } from '@/components/common/HifyFormDialog.vue'
import { useConfirm } from '@/composables/useConfirm'
import { notifySuccess } from '@/utils/notify'
import {
  getProviderList,
  createProvider,
  updateProvider,
  deleteProvider,
  testConnection,
  getProviderModels,
  type ProviderResponse,
  type ProviderModelResponse,
  type ProviderCreateData,
  type ProviderUpdateData,
} from '@/api/provider'

// =========================================================================
// Table
// =========================================================================

const tableRef = ref<{ refresh: () => void; toggleRowExpansion: (row: ProviderResponse, expanded?: boolean) => void }>()

/** 窄屏断点（与侧边栏折叠保持一致） */
const TABLE_COLLAPSE_BREAKPOINT = 1200
const isNarrow = ref(false)

function handleTableResize(): void {
  isNarrow.value = window.innerWidth < TABLE_COLLAPSE_BREAKPOINT
}

onMounted(() => {
  handleTableResize()
  window.addEventListener('resize', handleTableResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleTableResize)
})

const allColumns: Column[] = [
  { label: '名称', prop: 'name', minWidth: 140 },
  { label: '类型', slot: 'providerCode', width: 100, align: 'center' },
  { label: 'Base URL', prop: 'baseUrl', minWidth: 200 },
  { label: '健康状态', slot: 'health', width: 150, align: 'center' },
  { label: '模型数', slot: 'modelCount', width: 80, align: 'center' },
  { label: '状态', slot: 'status', width: 80, align: 'center' },
  { label: '创建时间', slot: 'createdAt', width: 110, align: 'center' },
  { label: '操作', slot: 'actions', width: 210, fixed: 'right' },
]

/** 窄屏下隐藏 Base URL、健康状态和创建时间 */
const hiddenOnNarrow = new Set(['baseUrl', 'health', 'createdAt'])

const columns = computed(() =>
  isNarrow.value
    ? allColumns.filter((col) => !hiddenOnNarrow.has(col.prop ?? ''))
    : allColumns,
)

async function fetchProviders(params: PageParams) {
  return getProviderList({
    page: params.page,
    pageSize: params.pageSize,
  })
}

// =========================================================================
// Tag helpers
// =========================================================================

// 与后端 ProviderAdapterFactory 注册的 providerCode 一一对应
const PROVIDER_CODE_LABELS: Record<string, string> = {
  openai: 'OpenAI',
  claude: 'Claude',
  ollama: 'Ollama',
  openai_compatible: '兼容',
}

function getProviderCodeTagType(code: unknown): 'success' | 'warning' | 'info' | 'danger' | 'primary' {
  const key = String(code ?? '')
  const map: Record<string, 'success' | 'warning' | 'info' | 'danger' | 'primary'> = {
    openai: 'primary',
    claude: 'success',
    ollama: 'info',
    openai_compatible: 'info',
  }
  return map[key] || 'info'
}

function getHealthTagType(healthStatus: unknown): 'success' | 'danger' | 'warning' | 'info' {
  const key = String(healthStatus ?? '')
  const map: Record<string, 'success' | 'danger' | 'warning' | 'info'> = {
    HEALTHY: 'success',
    UNHEALTHY: 'danger',
    DEGRADED: 'warning',
    UNKNOWN: 'info',
  }
  return map[key] || 'info'
}

function getHealthLabel(healthStatus: unknown): string {
  const key = String(healthStatus ?? '')
  const map: Record<string, string> = {
    HEALTHY: 'UP',
    UNHEALTHY: 'DOWN',
    DEGRADED: 'DEGRADED',
    UNKNOWN: 'UNKNOWN',
  }
  return map[key] || key
}

function formatDate(dateStr: unknown): string {
  if (!dateStr || typeof dateStr !== 'string') return '-'
  return dateStr.substring(0, 10)
}

// =========================================================================
// Expand: model list
// =========================================================================

const expandedModels = ref<Record<number, ProviderModelResponse[]>>({})
const loadingModels = ref<Record<number, boolean>>({})

function handleExpandChange(row: Record<string, unknown>, expandedRows: Record<string, unknown>[]): void {
  const providerRow = row as unknown as ProviderResponse
  const isExpanded = expandedRows.some((r) => (r as unknown as ProviderResponse).id === providerRow.id)
  if (isExpanded && !expandedModels.value[providerRow.id]) {
    loadModels(providerRow.id)
  }
}

async function loadModels(providerId: number): Promise<void> {
  loadingModels.value[providerId] = true
  try {
    expandedModels.value[providerId] = await getProviderModels(providerId)
  } catch {
    // error already handled by interceptor
  } finally {
    loadingModels.value[providerId] = false
  }
}

// =========================================================================
// Dialog (create / edit)
// =========================================================================

const dialogVisible = ref(false)
const dialogRef = ref<{ open: (data?: Record<string, unknown>) => void; isEdit: boolean }>()
const editingId = ref<number | null>(null)

// 注意：apiKey 不设必填 —— Ollama 本地无需 key；编辑时后端不回传明文，
// 留空表示不修改（handleSubmit 仅在非空时携带 authConfig）
const formRules: FormRules = {
  name: [{ required: true, message: '请输入提供商名称', trigger: 'blur' }],
  providerCode: [{ required: true, message: '请选择提供商类型', trigger: 'change' }],
}

function handleCreate(): void {
  editingId.value = null
  dialogRef.value?.open()
}

function handleEdit(row: Record<string, unknown>): void {
  const p = row as unknown as ProviderResponse
  editingId.value = p.id
  dialogRef.value?.open({
    name: p.name,
    providerCode: p.providerCode,
    apiKey: '',
    baseUrl: p.baseUrl || '',
    description: p.description || '',
  })
}

async function handleSubmit(data: FormData): Promise<void> {
  const authConfig = data.apiKey ? { apiKey: data.apiKey as string } : undefined

  if (editingId.value) {
    const updateData: ProviderUpdateData = {
      name: data.name as string,
      description: (data.description as string) || undefined,
      baseUrl: (data.baseUrl as string) || undefined,
    }
    if (authConfig) {
      updateData.authConfig = authConfig
    }
    await updateProvider(editingId.value, updateData)
    notifySuccess('更新成功')
  } else {
    const createData: ProviderCreateData = {
      name: data.name as string,
      providerCode: data.providerCode as string,
      authConfig,
      baseUrl: (data.baseUrl as string) || undefined,
      description: (data.description as string) || undefined,
    }
    await createProvider(createData)
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
  const p = row as unknown as ProviderResponse
  const ok = await confirmDelete(`确定删除提供商「${p.name}」？`, () => deleteProvider(p.id))
  if (ok) {
    tableRef.value?.refresh()
  }
}

// =========================================================================
// Test Connection
// =========================================================================

const testingIds = ref<Set<number>>(new Set())

async function handleTestConnection(row: Record<string, unknown>): Promise<void> {
  const p = row as unknown as ProviderResponse
  if (testingIds.value.has(p.id)) return
  testingIds.value = new Set(testingIds.value).add(p.id)
  try {
    const result = await testConnection(p.id)
    if (result.success) {
      ElMessage.success(`连通成功！延迟 ${result.latencyMs}ms，发现 ${result.modelCount} 个模型`)
    } else {
      ElMessage.warning(`连通失败：${result.errorMessage || '未知错误'}`)
    }
    tableRef.value?.refresh()
  } catch {
    // error already handled by interceptor
  } finally {
    const next = new Set(testingIds.value)
    next.delete(p.id)
    testingIds.value = next
  }
}
</script>

<template>
  <PageHeader
    title="模型提供商管理"
    description="配置和管理 LLM 模型提供商（OpenAI / Anthropic / Ollama）"
  >
    <template #actions>
      <el-button type="primary" :icon="Plus" @click="handleCreate">新增提供商</el-button>
    </template>
  </PageHeader>

  <HifyTable
    ref="tableRef"
    :columns="columns"
    :api="fetchProviders"
    :expandable="true"
    @expand-change="handleExpandChange"
  >
    <!-- 展开行：模型列表 -->
    <template #expand="{ row }">
      <div class="expand-models" v-loading="loadingModels[row.id]">
        <template v-if="expandedModels[row.id]?.length">
          <div class="expand-models__title">
            已启用模型（{{ expandedModels[row.id].filter(m => m.status === 'ENABLED').length }}）
          </div>
          <el-table
            :data="expandedModels[row.id]"
            size="small"
            class="expand-models__table"
          >
            <el-table-column prop="displayName" label="名称" min-width="140">
              <template #default="{ row: m }">
                {{ m.displayName || m.modelName }}
              </template>
            </el-table-column>
            <el-table-column prop="modelName" label="模型 ID" min-width="180" show-overflow-tooltip />
            <el-table-column prop="modelType" label="类型" width="90" align="center" />
            <el-table-column prop="contextWindow" label="上下文" width="90" align="center">
              <template #default="{ row: m }">
                {{ m.contextWindow ? (m.contextWindow / 1000).toFixed(0) + 'K' : '-' }}
              </template>
            </el-table-column>
            <el-table-column label="能力" width="160" align="center">
              <template #default="{ row: m }">
                <span class="capability-tags">
                  <el-tag v-if="m.supportsVision" size="small" type="success" effect="plain">视觉</el-tag>
                  <el-tag v-if="m.supportsTools" size="small" type="warning" effect="plain">工具</el-tag>
                  <el-tag v-if="m.supportsStreaming" size="small" effect="plain">流式</el-tag>
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="80" align="center">
              <template #default="{ row: m }">
                <el-tag :type="m.status === 'ENABLED' ? 'success' : 'info'" size="small" effect="light">
                  {{ m.status === 'ENABLED' ? '启用' : '禁用' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </template>
        <el-empty v-else-if="!loadingModels[row.id]" description="暂无模型" :image-size="48" />
      </div>
    </template>

    <!-- 类型 -->
    <template #providerCode="{ row }">
      <el-tag :type="getProviderCodeTagType(row.providerCode)" size="small" effect="light">
        {{ PROVIDER_CODE_LABELS[row.providerCode] || row.providerCode }}
      </el-tag>
    </template>

    <!-- 健康状态 -->
    <template #health="{ row }">
      <div class="health-cell">
        <el-tag
          :type="getHealthTagType(row.healthStatus)"
          size="small"
          effect="light"
        >
          {{ getHealthLabel(row.healthStatus) }}
        </el-tag>
        <span v-if="row.lastHealthResponseTimeMs != null" class="health-latency">
          {{ row.lastHealthResponseTimeMs }}ms
        </span>
      </div>
    </template>

    <!-- 模型数（点击可展开） -->
    <template #modelCount="{ row }">
      <el-button
        type="primary"
        link
        size="small"
        @click="tableRef?.toggleRowExpansion(row)"
      >
        {{ row.modelCount ?? 0 }}
      </el-button>
    </template>

    <!-- 状态 -->
    <template #status="{ row }">
      <el-tag
        :type="row.status === 'ENABLED' ? 'success' : 'info'"
        size="small"
        effect="light"
      >
        {{ row.status === 'ENABLED' ? '启用' : '禁用' }}
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
        <el-button
          size="small"
          :loading="testingIds.has(row.id)"
          @click="handleTestConnection(row)"
        >
          测试
        </el-button>
        <el-button type="danger" text size="small" @click="handleDelete(row)">删除</el-button>
      </div>
    </template>
  </HifyTable>

  <HifyFormDialog
    ref="dialogRef"
    v-model="dialogVisible"
    :title="editingId ? '编辑提供商' : '新增提供商'"
    :rules="formRules"
    label-width="100px"
    width="560px"
    @submit="handleSubmit"
  >
    <template #default="{ formData }">
      <el-form-item label="名称" prop="name">
        <el-input v-model="formData.name" placeholder="例如：OpenAI 官方" maxlength="50" show-word-limit />
      </el-form-item>
      <el-form-item label="类型" prop="providerCode">
        <el-select v-model="formData.providerCode" placeholder="请选择提供商类型" style="width: 100%">
          <el-option label="OpenAI" value="openai" />
          <el-option label="Anthropic（Claude）" value="claude" />
          <el-option label="Ollama" value="ollama" />
          <el-option label="OpenAI 兼容" value="openai_compatible" />
        </el-select>
      </el-form-item>
      <el-form-item label="API Key" prop="apiKey">
        <el-input
          v-model="formData.apiKey"
          :placeholder="editingId ? '留空表示不修改' : 'sk-...（Ollama 可留空）'"
          type="password"
          show-password
        />
      </el-form-item>
      <el-form-item label="Base URL" prop="baseUrl">
        <el-input v-model="formData.baseUrl" placeholder="https://api.openai.com/v1" />
      </el-form-item>
      <el-form-item label="描述" prop="description">
        <el-input
          v-model="formData.description"
          type="textarea"
          :rows="2"
          placeholder="可选：备注说明"
          maxlength="200"
          show-word-limit
        />
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

.health-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.health-latency {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.expand-models {
  padding: var(--hify-space-3) var(--hify-space-4);
  background: var(--hify-bg-page);
}

.expand-models__title {
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-secondary);
  margin-bottom: var(--hify-space-2);
}

.expand-models__table {
  background: var(--el-bg-color);
}

.capability-tags {
  display: flex;
  gap: 4px;
  justify-content: center;
}

:deep(.el-table__body tr:hover > td) {
  background-color: var(--hify-bg-hover) !important;
}

:deep(.el-table__body tr) {
  transition: background-color var(--hify-transition-fast);
}
</style>
