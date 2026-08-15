<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/components/common/PageHeader.vue'
import { debugMcpTool, getMcpServer, type McpServerDetailResponse } from '@/api/mcp'
import { notifyError, notifyWarning } from '@/utils/notify'

interface SchemaProperty {
  type?: string
  description?: string
}

interface InputSchema {
  type?: string
  properties?: Record<string, SchemaProperty>
  required?: string[]
}

interface DebugRecord {
  id: number
  toolName: string
  argumentsText: string
  result: string
  elapsedMs: number
  calledAt: string
  isError: boolean
}

const route = useRoute()
const router = useRouter()

const serverId = Number(route.params.id)
const activeTab = ref(route.query.tab === 'debug' ? 'debug' : 'info')
const server = ref<McpServerDetailResponse | null>(null)
const loading = ref(false)
const calling = ref(false)

const selectedToolName = ref('')
const formModel = reactive<Record<string, unknown>>({})
const history = ref<DebugRecord[]>([])
const recordSeq = ref(0)

const tools = computed(() => server.value?.tools ?? [])
const selectedTool = computed(() => tools.value.find((t) => t.toolName === selectedToolName.value) ?? null)

const schema = computed<InputSchema>(() => {
  const raw = selectedTool.value?.inputSchema
  if (!raw) return {}
  try {
    return JSON.parse(raw) as InputSchema
  } catch {
    return {}
  }
})

const schemaProperties = computed(() => Object.entries(schema.value.properties ?? {}))
const requiredFields = computed(() => schema.value.required ?? [])

function labelOf(key: string): string {
  return schema.value.properties?.[key]?.description || key
}

function isNumberField(key: string): boolean {
  const type = schema.value.properties?.[key]?.type
  return type === 'number' || type === 'integer'
}

function isRequired(key: string): boolean {
  return requiredFields.value.includes(key)
}

function asString(value: unknown): string {
  if (typeof value === 'string') return value
  return value == null ? '' : String(value)
}

function asNumber(value: unknown): number | undefined {
  return typeof value === 'number' ? value : undefined
}

function updateValue(key: string, value: unknown): void {
  formModel[key] = value
}

function resetForm(): void {
  for (const key of Object.keys(formModel)) {
    delete formModel[key]
  }
}

async function loadServer(): Promise<void> {
  loading.value = true
  try {
    server.value = await getMcpServer(serverId)
    if (tools.value.length > 0 && !selectedToolName.value) {
      selectedToolName.value = tools.value[0].toolName
    }
  } catch {
    notifyError('加载 MCP Server 详情失败')
  } finally {
    loading.value = false
  }
}

function validateRequired(): string | null {
  for (const key of requiredFields.value) {
    const value = formModel[key]
    if (value === undefined || value === null || value === '') {
      return `请填写：${labelOf(key)}`
    }
  }
  return null
}

function pushRecord(toolName: string, args: Record<string, unknown>, result: string, elapsedMs: number, isError: boolean): void {
  recordSeq.value += 1
  history.value.unshift({
    id: recordSeq.value,
    toolName,
    argumentsText: JSON.stringify(args),
    result,
    elapsedMs,
    calledAt: new Date().toLocaleTimeString(),
    isError,
  })
  history.value = history.value.slice(0, 5)
}

async function callTool(): Promise<void> {
  if (!selectedTool.value || calling.value) return
  const missing = validateRequired()
  if (missing) {
    notifyWarning(missing)
    return
  }

  calling.value = true
  const args: Record<string, unknown> = { ...formModel }
  try {
    const res = await debugMcpTool(serverId, selectedTool.value.toolName, args)
    pushRecord(selectedTool.value.toolName, args, res.result, res.elapsedMs, false)
  } catch (error) {
    const message = error instanceof Error ? error.message : '调用失败'
    pushRecord(selectedTool.value.toolName, args, message, 0, true)
  } finally {
    calling.value = false
  }
}

watch(selectedToolName, () => {
  resetForm()
})

onMounted(() => {
  loadServer()
})
</script>

<template>
  <PageHeader title="MCP Server 详情" :description="server?.name">
    <template #actions>
      <el-button :icon="ArrowLeft" @click="router.back()">返回</el-button>
      <el-button :icon="Refresh" :loading="loading" @click="loadServer">刷新</el-button>
    </template>
  </PageHeader>

  <el-tabs v-model="activeTab" class="mcp-detail-tabs">
    <el-tab-pane label="基本信息" name="info">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="名称">{{ server?.name }}</el-descriptions-item>
        <el-descriptions-item label="Endpoint">{{ server?.endpoint }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="server?.enabled ? 'success' : 'info'">
            {{ server?.enabled ? '已启用' : '已停用' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="工具数">{{ server?.toolCount }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ server?.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ server?.updatedAt }}</el-descriptions-item>
      </el-descriptions>
    </el-tab-pane>

    <el-tab-pane label="调试" name="debug">
      <div v-loading="loading" class="debug-layout">
        <aside class="debug-toolbar">
          <div class="debug-toolbar__title">工具列表</div>
          <el-empty v-if="tools.length === 0" description="暂无工具" :image-size="60" />
          <div
            v-for="tool in tools"
            :key="tool.id"
            class="debug-toolbar__item"
            :class="{ active: tool.toolName === selectedToolName }"
            @click="selectedToolName = tool.toolName"
          >
            <span class="debug-toolbar__name">{{ tool.toolName }}</span>
          </div>
        </aside>

        <section v-if="selectedTool" class="debug-panel">
          <div class="debug-panel__header">
            <div class="debug-panel__title">{{ selectedTool.toolName }}</div>
            <div class="debug-panel__desc">{{ selectedTool.description || '该工具暂无描述' }}</div>
          </div>

          <el-form label-position="top" class="debug-form">
            <el-form-item
              v-for="[key] in schemaProperties"
              :key="key"
              :label="labelOf(key)"
              :required="isRequired(key)"
            >
              <el-input
                v-if="!isNumberField(key)"
                :model-value="asString(formModel[key])"
                :placeholder="`请输入${labelOf(key)}`"
                @update:model-value="(value: string | number | null) => updateValue(key, value)"
              />
              <el-input-number
                v-else
                :model-value="asNumber(formModel[key])"
                :controls="false"
                :placeholder="`请输入${labelOf(key)}`"
                style="width: 100%"
                @update:model-value="(value: number | undefined) => updateValue(key, value)"
              />
            </el-form-item>
            <el-form-item v-if="schemaProperties.length === 0" label="参数">
              <el-input model-value="该工具无需参数" disabled />
            </el-form-item>
          </el-form>

          <div class="debug-panel__actions">
            <el-button type="primary" :loading="calling" :disabled="calling" @click="callTool">
              {{ calling ? '调用中...' : '调用' }}
            </el-button>
          </div>

          <div v-if="history.length > 0" class="debug-result">
            <div class="debug-result__head">
              <span class="debug-result__label">最近调用</span>
              <span class="debug-result__meta">
                {{ history[0].calledAt }} · {{ history[0].elapsedMs }}ms
              </span>
            </div>
            <pre class="debug-result__body" :class="{ 'is-error': history[0].isError }">{{ history[0].result }}</pre>
          </div>

          <div v-if="history.length > 0" class="debug-history">
            <div class="debug-history__title">最近 5 次调用记录</div>
            <div v-for="record in history" :key="record.id" class="debug-history__item">
              <div class="debug-history__row">
                <span class="debug-history__tool">{{ record.toolName }}</span>
                <span class="debug-history__meta">{{ record.calledAt }} · {{ record.elapsedMs }}ms</span>
              </div>
              <div class="debug-history__args">{{ record.argumentsText }}</div>
              <pre class="debug-history__result" :class="{ 'is-error': record.isError }">{{ record.result }}</pre>
            </div>
          </div>
        </section>

        <el-empty v-else class="debug-panel debug-panel--empty" description="请选择左侧工具" />
      </div>
    </el-tab-pane>
  </el-tabs>
</template>

<style scoped>
.mcp-detail-tabs {
  min-height: 320px;
}

.debug-layout {
  display: flex;
  align-items: flex-start;
  gap: var(--hify-section-gap);
  min-height: 360px;
}

.debug-toolbar {
  width: 220px;
  min-width: 220px;
  border: 1px solid var(--hify-border-light);
  border-radius: var(--hify-radius-card);
  overflow: hidden;
}

.debug-toolbar__title {
  padding: 12px 16px;
  font-size: 14px;
  font-weight: 600;
  color: var(--hify-text-secondary);
  border-bottom: 1px solid var(--hify-border-light);
  background: var(--hify-bg-fill);
}

.debug-toolbar__item {
  padding: 11px 16px;
  cursor: pointer;
  font-size: 13px;
  color: var(--hify-text-secondary);
  transition: background-color 150ms ease, color 150ms ease;
}

.debug-toolbar__item + .debug-toolbar__item {
  border-top: 1px solid var(--hify-border-light);
}

.debug-toolbar__item:hover {
  background: var(--hify-bg-hover);
}

.debug-toolbar__item.active {
  color: var(--hify-primary-600);
  background: var(--hify-primary-50);
  font-weight: 600;
}

.debug-toolbar__name {
  word-break: break-all;
}

.debug-panel {
  flex: 1;
  min-width: 0;
  border: 1px solid var(--hify-border-light);
  border-radius: var(--hify-radius-card);
  padding: var(--hify-card-padding);
}

.debug-panel--empty {
  display: flex;
  align-items: center;
  justify-content: center;
}

.debug-panel__header {
  padding-bottom: var(--hify-section-gap);
  border-bottom: 1px solid var(--hify-border-light);
}

.debug-panel__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--hify-text-primary);
}

.debug-panel__desc {
  margin-top: 6px;
  font-size: 13px;
  line-height: var(--hify-line-height-base);
  color: var(--hify-text-tertiary);
}

.debug-form {
  margin-top: var(--hify-section-gap);
  max-width: 560px;
}

.debug-panel__actions {
  margin-top: var(--hify-section-gap);
}

.debug-result {
  margin-top: var(--hify-section-gap);
}

.debug-result__head,
.debug-history__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.debug-result__label,
.debug-history__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--hify-text-primary);
}

.debug-result__meta,
.debug-history__meta {
  font-size: 12px;
  color: var(--hify-text-tertiary);
}

.debug-result__body,
.debug-history__result {
  margin: 10px 0 0;
  padding: 12px;
  border-radius: 6px;
  background: var(--hify-bg-fill);
  border: 1px solid var(--hify-border-light);
  font-size: 12px;
  line-height: var(--hify-line-height-base);
  color: var(--hify-text-secondary);
  white-space: pre-wrap;
  word-break: break-all;
}

.debug-result__body.is-error,
.debug-history__result.is-error {
  color: var(--hify-danger-600);
  border-color: var(--hify-danger-200);
  background: var(--hify-danger-50);
}

.debug-history {
  margin-top: var(--hify-section-gap);
}

.debug-history__item {
  margin-top: 12px;
  padding: 12px;
  border: 1px solid var(--hify-border-light);
  border-radius: 6px;
}

.debug-history__tool {
  font-size: 13px;
  font-weight: 600;
  color: var(--hify-text-secondary);
}

.debug-history__args {
  margin-top: 8px;
  font-size: 12px;
  color: var(--hify-text-tertiary);
  word-break: break-all;
}
</style>
