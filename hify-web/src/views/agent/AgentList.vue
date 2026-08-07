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
  getAgent,
  createAgent,
  updateAgent,
  updateAgentTools,
  deleteAgent,
  listToolDefinitions,
  listModelConfigs,
  type AgentListResponse,
  type AgentCreateData,
  type AgentUpdateData,
  type ToolDefinitionResponse,
  type AgentToolData,
} from '@/api/agent'
import { getProviderList } from '@/api/provider'

// =========================================================================
// Table
// =========================================================================

const tableRef = ref<{ refresh: () => void }>()

/** 模型 ID → 模型展示名 的映射表 */
const modelNameMap = ref<Map<number, string>>(new Map())

const columns: Column[] = [
  { label: '名称', prop: 'name', minWidth: 140 },
  { label: '关联模型', slot: 'modelName', minWidth: 160 },
  { label: '工具数', slot: 'toolCount', width: 80, align: 'center' },
  { label: '温度', prop: 'temperature', width: 80, align: 'center' },
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

function getModelDisplayName(modelConfigId: number): string {
  const name = modelNameMap.value.get(modelConfigId)
  return name || `ID:${modelConfigId}`
}

// =========================================================================
// Model list loading (for name lookup in table + dropdown in form)
// =========================================================================

let modelConfigsLoaded = false

async function ensureModelConfigsLoaded(): Promise<void> {
  if (modelConfigsLoaded) return
  try {
    const models = await listModelConfigs()
    const map = new Map<number, string>()
    for (const m of models) {
      map.set(m.id, m.displayName || m.modelName)
    }
    modelNameMap.value = map
    modelConfigsLoaded = true
  } catch {
    // 模型列表加载失败不影响表格展示
  }
}

// =========================================================================
// Dialog (create / edit)
// =========================================================================

const dialogVisible = ref(false)
const dialogRef = ref<{ open: (data?: Record<string, unknown>) => void; isEdit: boolean }>()
const editingId = ref<number | null>(null)
const formLoading = ref(false)

const formRules: FormRules = {
  name: [{ required: true, message: '请输入 Agent 名称', trigger: 'blur' }],
  systemPrompt: [{ required: true, message: '请输入系统提示词', trigger: 'blur' }],
  modelConfigId: [{ required: true, message: '请选择模型', trigger: 'change' }],
}

// ---- 模型下拉数据 ----

interface ModelOption {
  id: number
  label: string
  providerId: number
}

interface ProviderGroup {
  id: number
  name: string
  models: ModelOption[]
}

const providerGroups = ref<ProviderGroup[]>([])

// ---- 工具绑定数据 ----

const allToolDefinitions = ref<ToolDefinitionResponse[]>([])
/** 当前 Agent 已有工具的 toolName 集合（编辑模式下用来回显选中项） */
const existingToolNames = ref<Set<string>>(new Set())
/** 工具类型标签映射 */
const TOOL_TYPE_LABELS: Record<string, string> = {
  MCP: 'MCP',
  BUILTIN: '内置',
  HTTP: 'HTTP',
}

async function loadFormReferenceData(): Promise<void> {
  formLoading.value = true
  try {
    const [models, providers, tools] = await Promise.all([
      listModelConfigs(),
      getProviderList({ page: 1, pageSize: 100 }),
      listToolDefinitions(),
    ])

    // 构建提供商名称映射
    const providerNameMap = new Map<number, string>()
    for (const p of providers.list) {
      providerNameMap.set(p.id, p.name)
    }

    // 按提供商分组模型
    const groupMap = new Map<number, ProviderGroup>()
    for (const m of models) {
      if (m.status !== 'ENABLED') continue
      if (!groupMap.has(m.providerId)) {
        groupMap.set(m.providerId, {
          id: m.providerId,
          name: providerNameMap.get(m.providerId) || `Provider#${m.providerId}`,
          models: [],
        })
      }
      groupMap.get(m.providerId)!.models.push({
        id: m.id,
        label: m.displayName || m.modelName,
        providerId: m.providerId,
      })
    }
    providerGroups.value = Array.from(groupMap.values())

    allToolDefinitions.value = tools
  } finally {
    formLoading.value = false
  }
}

// ---- 打开弹窗 ----

function handleCreate(): void {
  editingId.value = null
  existingToolNames.value = new Set()
  loadFormReferenceData().then(() => {
    dialogRef.value?.open({
      status: 'ENABLED',
      temperature: 0.7,
      maxTokens: 4096,
      maxContextTurns: 10,
      selectedToolIds: [],
    })
  })
}

async function handleEdit(row: Record<string, unknown>): Promise<void> {
  const a = row as unknown as AgentListResponse
  editingId.value = a.id
  await loadFormReferenceData()

  // 获取 Agent 详情以拿到已绑定的工具列表
  let currentToolNames: string[] = []
  try {
    const detail = await getAgent(a.id)
    currentToolNames = (detail.tools || []).map((t) => t.toolName)
  } catch {
    // 获取详情失败不影响编辑
  }
  existingToolNames.value = new Set(currentToolNames)

  // 匹配已绑定的工具到 tool definition ID
  const selectedIds: number[] = []
  for (const def of allToolDefinitions.value) {
    if (currentToolNames.includes(def.toolName)) {
      selectedIds.push(def.id)
    }
  }

  dialogRef.value?.open({
    name: a.name,
    description: a.description || '',
    systemPrompt: a.systemPrompt,
    modelConfigId: a.modelConfigId,
    status: a.status,
    temperature: a.temperature ?? 0.7,
    maxTokens: a.maxTokens ?? 4096,
    maxContextTurns: a.maxContextTurns ?? 10,
    selectedToolIds: selectedIds,
  })
}

// ---- 提交 ----

async function handleSubmit(data: FormData): Promise<void> {
  const selectedToolIds = (data.selectedToolIds as number[]) || []

  if (editingId.value) {
    // -- 编辑模式 --
    const updateData: AgentUpdateData = {
      name: data.name as string,
      description: (data.description as string) || undefined,
      systemPrompt: data.systemPrompt as string | undefined,
      modelId: data.modelConfigId != null ? Number(data.modelConfigId) : undefined,
      temperature: data.temperature != null ? Number(data.temperature) : undefined,
      maxTokens: data.maxTokens != null ? Number(data.maxTokens) : undefined,
      maxIterations: data.maxContextTurns != null ? Number(data.maxContextTurns) : undefined,
      toolsEnabled: selectedToolIds.length > 0 ? 1 : undefined,
      status: (data.status as string) || undefined,
    }
    await updateAgent(editingId.value, updateData)

    // 更新工具绑定
    const toolData: AgentToolData[] = selectedToolIds
      .map((id) => allToolDefinitions.value.find((d) => d.id === id))
      .filter(Boolean)
      .map((def, idx) => ({
        toolName: def!.toolName,
        toolType: def!.toolType,
        toolConfig: def!.toolConfig || undefined,
        priority: idx,
      }))
    await updateAgentTools(editingId.value, toolData)

    notifySuccess('更新成功')
  } else {
    // -- 创建模式 --
    const createData: AgentCreateData = {
      name: data.name as string,
      description: (data.description as string) || undefined,
      systemPrompt: data.systemPrompt as string,
      modelConfigId: Number(data.modelConfigId),
      temperature: data.temperature != null ? Number(data.temperature) : undefined,
      maxTokens: data.maxTokens != null ? Number(data.maxTokens) : undefined,
      maxContextTurns: data.maxContextTurns != null ? Number(data.maxContextTurns) : undefined,
      toolsEnabled: selectedToolIds.length > 0 ? 1 : 0,
      status: (data.status as string) || undefined,
      toolIds: selectedToolIds.length > 0 ? selectedToolIds : undefined,
    }
    await createAgent(createData)
    notifySuccess('创建成功')
  }

  dialogVisible.value = false
  // 刷新模型名称映射（新增的 agent 可能用了新模型）
  modelConfigsLoaded = false
  ensureModelConfigsLoaded()
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

// =========================================================================
// Init
// =========================================================================

ensureModelConfigsLoaded()
</script>

<template>
  <PageHeader
    title="Agent 管理"
    description="创建和管理 AI Agent，配置系统提示词、模型和工具集"
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
    <!-- 关联模型名 -->
    <template #modelName="{ row }">
      <span>{{ getModelDisplayName(row.modelConfigId) }}</span>
    </template>

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
    width="720px"
    @submit="handleSubmit"
  >
    <template #default="{ formData }">
      <!-- 基本信息 -->
      <el-form-item label="名称" prop="name">
        <el-input v-model="formData.name" placeholder="例如：客服助手" maxlength="128" show-word-limit />
      </el-form-item>

      <el-form-item label="描述" prop="description">
        <el-input
          v-model="formData.description"
          type="textarea"
          :rows="2"
          placeholder="可选：简要描述 Agent 的用途"
          maxlength="512"
          show-word-limit
        />
      </el-form-item>

      <el-form-item label="状态" prop="status">
        <el-radio-group v-model="formData.status">
          <el-radio value="ENABLED">启用</el-radio>
          <el-radio value="DISABLED">禁用</el-radio>
          <el-radio value="DRAFT">草稿</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="模型选择" prop="modelConfigId">
        <el-select
          v-model="formData.modelConfigId"
          placeholder="请选择模型"
          style="width: 100%"
          :loading="formLoading"
        >
          <el-option-group
            v-for="group in providerGroups"
            :key="group.id"
            :label="group.name"
          >
            <el-option
              v-for="m in group.models"
              :key="m.id"
              :label="m.label"
              :value="m.id"
            />
          </el-option-group>
        </el-select>
      </el-form-item>

      <el-form-item label="系统提示词" prop="systemPrompt">
        <el-input
          v-model="formData.systemPrompt"
          type="textarea"
          :rows="6"
          placeholder="定义 Agent 的角色、行为和约束..."
        />
      </el-form-item>

      <!-- 参数配置 -->
      <el-form-item label="温度">
        <div class="slider-row">
          <el-slider
            v-model="formData.temperature"
            :min="0"
            :max="1"
            :step="0.1"
            :marks="{ 0: '0', 0.5: '0.5', 1: '1' }"
            style="flex: 1; margin-right: 16px"
          />
          <span class="slider-value">{{ formData.temperature ?? 0.7 }}</span>
        </div>
      </el-form-item>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="最大 Token">
            <el-input-number
              v-model="formData.maxTokens"
              :min="1"
              :max="131072"
              :step="256"
              placeholder="默认 4096"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="上下文轮次">
            <el-input-number
              v-model="formData.maxContextTurns"
              :min="1"
              :max="50"
              placeholder="默认 10"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 工具绑定 -->
      <el-divider content-position="left">工具绑定</el-divider>
      <div class="tool-binding-area" v-loading="formLoading">
        <template v-if="allToolDefinitions.length > 0">
          <el-checkbox-group v-model="formData.selectedToolIds" class="tool-checkbox-group">
            <el-checkbox
              v-for="tool in allToolDefinitions"
              :key="tool.id"
              :label="tool.id"
              class="tool-checkbox-item"
            >
              <div class="tool-checkbox-label">
                <span class="tool-name">{{ tool.toolName }}</span>
                <el-tag size="small" type="info" effect="plain" class="tool-type-tag">
                  {{ TOOL_TYPE_LABELS[tool.toolType] || tool.toolType }}
                </el-tag>
              </div>
              <div v-if="tool.description" class="tool-desc">{{ tool.description }}</div>
            </el-checkbox>
          </el-checkbox-group>
        </template>
        <el-empty v-else description="暂无可用的工具定义" :image-size="60" />
      </div>
    </template>
  </HifyFormDialog>
</template>

<style scoped>
.action-btns {
  display: flex;
  align-items: center;
  gap: var(--hify-space-2);
}

.slider-row {
  display: flex;
  align-items: center;
  width: 100%;
}

.slider-value {
  width: 36px;
  text-align: right;
  font-size: var(--hify-font-size-sm);
  color: var(--hify-text-secondary);
  flex-shrink: 0;
}

.tool-binding-area {
  max-height: 240px;
  overflow-y: auto;
  padding: var(--hify-space-2) 0;
}

.tool-checkbox-group {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.tool-checkbox-item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  margin-right: 0;
  padding: var(--hify-space-1) var(--hify-space-2);
  border-radius: var(--hify-radius-sm);
  transition: background-color var(--hify-transition-fast);
}

.tool-checkbox-item:hover {
  background-color: var(--hify-bg-hover);
}

.tool-checkbox-label {
  display: flex;
  align-items: center;
  gap: var(--hify-space-2);
}

.tool-name {
  font-weight: 500;
}

.tool-type-tag {
  flex-shrink: 0;
}

.tool-desc {
  font-size: var(--hify-font-size-xs);
  color: var(--hify-text-tertiary);
  margin-top: 2px;
  margin-left: 24px;
}
</style>
