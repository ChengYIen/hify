<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, CopyDocument, Monitor, Refresh } from '@element-plus/icons-vue'
import {
  debugMcpTool,
  getMcpServer,
  testMcpServer,
  type McpServerDetailResponse,
  type McpTool,
} from '@/api/mcp'
import { notifySuccess, notifyWarning } from '@/utils/notify'

interface SchemaProperty {
  type?: string
  description?: string
}

interface InputSchema {
  type?: string
  properties?: Record<string, SchemaProperty>
  required?: string[]
}

interface CallResult {
  ok: boolean
  text: string
  elapsedMs?: number
  status?: number
}

const route = useRoute()
const router = useRouter()

const serverId = Number(route.params.id)
const serverName = ref(String(route.query.name || ''))
const serverEndpoint = ref(String(route.query.endpoint || ''))
const server = ref<McpServerDetailResponse | null>(null)
const loading = ref(false)
const refreshing = ref(false)

const status = ref<'checking' | 'connected' | 'unreachable'>('checking')
const statusMessage = ref('正在检测连接...')

const selectedToolName = ref('')
const jsonMode = ref(true)
const paramsText = ref('{}')
const jsonError = ref('')
const formModel = reactive<Record<string, unknown>>({})
const calling = ref(false)
const result = ref<CallResult | null>(null)

const highlightRef = ref<HTMLElement | null>(null)
const paramsTextareaRef = ref<HTMLTextAreaElement | null>(null)
const resultRef = ref<HTMLElement | null>(null)

const toolOptions = computed(() => server.value?.tools ?? [])
const selectedTool = computed<McpTool | null>(
  () => toolOptions.value.find((tool) => tool.toolName === selectedToolName.value) ?? null,
)

const inputSchema = computed<InputSchema>(() => {
  const raw = selectedTool.value?.inputSchema
  if (!raw) return {}
  try {
    return JSON.parse(raw) as InputSchema
  } catch {
    return {}
  }
})

const schemaProps = computed(() => Object.entries(inputSchema.value.properties ?? {}))
const hasParams = computed(() => schemaProps.value.length > 0)

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

function syntaxHighlight(json: string): string {
  const regex =
    /("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+\-]?\d+)?)/g
  return json.replace(regex, (match) => {
    let cls = 'json-number'
    if (/^"/.test(match)) {
      cls = /:$/.test(match) ? 'json-key' : 'json-string'
    } else if (/true|false/.test(match)) {
      cls = 'json-bool'
    } else if (/null/.test(match)) {
      cls = 'json-null'
    }
    return `<span class="${cls}">${escapeHtml(match)}</span>`
  })
}

const highlightedJson = computed(() => {
  if (jsonError.value) return escapeHtml(paramsText.value)
  try {
    const parsed = JSON.parse(paramsText.value || '{}')
    return syntaxHighlight(JSON.stringify(parsed, null, 2))
  } catch {
    return escapeHtml(paramsText.value)
  }
})

function validateJson(): void {
  jsonError.value = ''
  try {
    JSON.parse(paramsText.value || '{}')
  } catch (error) {
    jsonError.value = error instanceof Error ? error.message : 'JSON 格式错误'
  }
}

function syncScroll(): void {
  const textarea = paramsTextareaRef.value
  const highlight = highlightRef.value
  if (textarea && highlight) {
    highlight.scrollTop = textarea.scrollTop
    highlight.scrollLeft = textarea.scrollLeft
  }
}

function resetParams(): void {
  for (const key of Object.keys(formModel)) {
    delete formModel[key]
  }
  paramsText.value = '{}'
  jsonError.value = ''
  for (const [key, prop] of schemaProps.value) {
    if (prop.type === 'number' || prop.type === 'integer') {
      formModel[key] = undefined
    } else {
      formModel[key] = ''
    }
  }
}

async function checkConnectivity(): Promise<void> {
  status.value = 'checking'
  statusMessage.value = '正在检测连接...'
  try {
    const test = await testMcpServer(serverId)
    if (test.success) {
      const detail = await getMcpServer(serverId)
      server.value = detail
      status.value = 'connected'
      statusMessage.value = '已连接'
      if (toolOptions.value.length && !toolOptions.value.some((t) => t.toolName === selectedToolName.value)) {
        selectedToolName.value = toolOptions.value[0].toolName
      }
    } else {
      status.value = 'unreachable'
      statusMessage.value = test.errorMessage || '无法连接'
    }
  } catch {
    status.value = 'unreachable'
    statusMessage.value = '无法连接'
  }
}

async function loadServer(): Promise<void> {
  loading.value = true
  try {
    const detail = await getMcpServer(serverId)
    server.value = detail
    serverName.value = serverName.value || detail.name || ''
    serverEndpoint.value = serverEndpoint.value || detail.endpoint || ''
    await checkConnectivity()
  } catch {
    status.value = 'unreachable'
    statusMessage.value = '无法连接'
    serverName.value = serverName.value || `MCP Server #${serverId}`
  } finally {
    loading.value = false
  }
}

async function refreshTools(): Promise<void> {
  refreshing.value = true
  try {
    await checkConnectivity()
    if (status.value === 'connected') {
      notifySuccess('工具列表已刷新')
    } else {
      notifyWarning(statusMessage.value || '无法连接')
    }
  } finally {
    refreshing.value = false
  }
}

function buildArgs(): Record<string, unknown> | null {
  if (jsonMode.value) {
    validateJson()
    if (jsonError.value) return null
    try {
      return JSON.parse(paramsText.value || '{}') as Record<string, unknown>
    } catch {
      return null
    }
  }
  const args: Record<string, unknown> = {}
  for (const [key] of schemaProps.value) {
    const value = formModel[key]
    if (value !== undefined && value !== null && value !== '') {
      args[key] = value
    }
  }
  return args
}

function formatResult(raw: string): string {
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

async function executeCall(): Promise<void> {
  if (!selectedToolName.value.trim()) {
    notifyWarning('请选择或输入工具名称')
    return
  }
  const args = buildArgs()
  if (args === null) return

  calling.value = true
  try {
    const res = await debugMcpTool(serverId, selectedToolName.value.trim(), args)
    result.value = {
      ok: true,
      text: formatResult(res.result),
      elapsedMs: res.elapsedMs,
    }
  } catch (error) {
    const err = error as { message?: string; response?: { status?: number } }
    result.value = {
      ok: false,
      text: err.message || '调用失败',
      status: err.response?.status,
    }
  } finally {
    calling.value = false
    await nextTick()
    resultRef.value?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
  }
}

function copyEndpoint(): void {
  if (!serverEndpoint.value) return
  navigator.clipboard?.writeText(serverEndpoint.value).then(() => {
    notifySuccess('Endpoint 已复制')
  })
}

function goBack(): void {
  router.push('/mcp-tools')
}

watch(selectedToolName, () => {
  resetParams()
  result.value = null
})

watch(jsonMode, (enabled) => {
  if (enabled) {
    validateJson()
  }
})

onMounted(() => {
  loadServer()
})
</script>

<template>
  <div class="mcp-debug-page" v-loading="loading">
    <div class="debug-header">
      <div class="debug-header-left">
        <el-button :icon="ArrowLeft" class="back-btn" @click="goBack">返回</el-button>
        <h1 class="server-title">{{ serverName || 'MCP Server' }}</h1>
        <span class="status-tag" :class="`status-${status}`">
          <i class="status-dot" />
          {{ status === 'checking' ? '检测中' : status === 'connected' ? '已连接' : '无法连接' }}
        </span>
      </div>
      <el-button :icon="Refresh" :loading="refreshing" @click="refreshTools">刷新工具</el-button>
    </div>

    <section class="info-card">
      <div class="info-item">
        <span class="info-label">名称</span>
        <span class="info-value">{{ serverName || '—' }}</span>
      </div>
      <div class="info-item">
        <span class="info-label">Endpoint</span>
        <span class="info-value endpoint-value">{{ serverEndpoint || '—' }}</span>
        <el-button
          v-if="serverEndpoint"
          link
          type="primary"
          :icon="CopyDocument"
          @click="copyEndpoint"
        >
          复制
        </el-button>
      </div>
      <div class="info-item">
        <span class="info-label">描述</span>
        <span class="info-value">{{ server?.description || '暂无描述' }}</span>
      </div>
    </section>

    <div class="debug-layout">
      <section class="debug-panel">
        <div class="panel-title">SERVER信息</div>

        <div class="field-block">
          <label class="field-label">选择工具</label>
          <el-select
            v-model="selectedToolName"
            class="tool-select"
            filterable
            clearable
            placeholder="从工具列表选择"
          >
            <el-option
              v-for="tool in toolOptions"
              :key="tool.id"
              :label="tool.toolName"
              :value="tool.toolName"
            >
              <div class="tool-option">
                <span class="tool-option-name">{{ tool.toolName }}</span>
                <span v-if="tool.description" class="tool-option-desc">{{ tool.description }}</span>
              </div>
            </el-option>
          </el-select>
        </div>

        <div class="field-block">
          <label class="field-label">手动输入工具名</label>
          <el-input
            v-model="selectedToolName"
            placeholder="可手动输入工具名，与下拉联动"
            clearable
          />
        </div>

        <div v-if="selectedTool" class="field-block">
          <label class="field-label">工具说明</label>
          <p class="tool-desc">{{ selectedTool.description || '该工具暂无描述' }}</p>
        </div>

        <div class="params-header">
          <label class="field-label">输入参数</label>
          <el-switch v-model="jsonMode" active-text="JSON模式" inactive-text="表单模式" />
        </div>

        <div v-if="!hasParams" class="no-params">该工具无需参数</div>

        <template v-else>
          <div v-if="jsonMode" class="json-editor" :class="{ 'is-error': jsonError }">
            <pre ref="highlightRef" class="json-highlight" v-html="highlightedJson" />
            <textarea
              ref="paramsTextareaRef"
              v-model="paramsText"
              class="json-textarea"
              spellcheck="false"
              @input="validateJson"
              @scroll="syncScroll"
            />
          </div>
          <p v-if="jsonError" class="json-error">{{ jsonError }}</p>

          <div v-else class="schema-form">
            <div v-for="[key, prop] in schemaProps" :key="key" class="schema-field">
              <label class="schema-label">
                {{ prop.description || key }}
                <span v-if="inputSchema.required?.includes(key)" class="required-mark">*</span>
              </label>
              <el-input
                v-if="prop.type === 'string'"
                v-model="formModel[key]"
                :placeholder="`请输入${prop.description || key}`"
              />
              <el-input-number
                v-else
                v-model="formModel[key]"
                :controls="false"
                class="number-input"
                :placeholder="`请输入${prop.description || key}`"
              />
            </div>
          </div>
        </template>

        <div class="call-row">
          <el-button
            type="primary"
            :icon="Monitor"
            :loading="calling"
            :disabled="calling"
            @click="executeCall"
          >
            {{ calling ? '执行中...' : '执行调用' }}
          </el-button>
          <span v-if="statusMessage" class="status-hint" :class="{ 'is-error': status === 'unreachable' }">
            {{ statusMessage }}
          </span>
        </div>
      </section>

      <section ref="resultRef" class="result-panel">
        <div class="panel-title">调用结果</div>
        <div v-if="!result" class="result-empty">
          <span class="result-empty-text">尚未调用，执行后结果会显示在这里</span>
        </div>
        <div v-else class="result-body" :class="result.ok ? 'is-success' : 'is-error'">
          <div class="result-head">
            <span class="result-status">{{ result.ok ? '成功' : '失败' }}</span>
            <span v-if="result.status" class="result-status-code">HTTP {{ result.status }}</span>
            <span v-if="result.elapsedMs != null" class="result-meta">{{ result.elapsedMs }}ms</span>
          </div>
          <pre class="result-text">{{ result.text }}</pre>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.mcp-debug-page {
  --mcp-primary: #1677ff;
  --mcp-primary-hover: #4096ff;
  --mcp-success: #52c41a;
  --mcp-danger: #ff4d4f;
  --mcp-warning: #faad14;
  --el-color-primary: var(--mcp-primary);
  --el-color-primary-light-3: #69b1ff;
  --el-color-primary-light-5: #a0cfff;
  --el-color-primary-light-7: #d6e4ff;
  --el-color-primary-light-8: #e6f4ff;
  --el-color-primary-light-9: #f0f7ff;
  --el-color-primary-dark-2: #0958d9;
  --el-color-success: var(--mcp-success);
  --el-color-danger: var(--mcp-danger);
  --el-color-warning: var(--mcp-warning);
  min-width: 0;
}

.debug-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.debug-header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.back-btn {
  flex-shrink: 0;
}

.server-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--hify-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

.status-tag .status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.status-connected {
  color: var(--mcp-success);
  background: rgba(82, 196, 26, 0.12);
}

.status-connected .status-dot {
  background: var(--mcp-success);
}

.status-unreachable {
  color: var(--mcp-danger);
  background: rgba(255, 77, 79, 0.12);
}

.status-unreachable .status-dot {
  background: var(--mcp-danger);
}

.status-checking {
  color: var(--hify-text-tertiary);
  background: rgba(134, 141, 161, 0.12);
}

.status-checking .status-dot {
  background: var(--hify-gray-400);
}

.info-card {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
  padding: 16px 20px;
  margin-bottom: 16px;
  border: 1px solid var(--hify-border-light);
  border-radius: 8px;
  background: var(--hify-bg-container);
}

.info-item {
  display: flex;
  align-items: flex-start;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.info-label {
  font-size: 12px;
  color: var(--hify-text-tertiary);
}

.info-value {
  font-size: 14px;
  font-weight: 600;
  color: var(--hify-text-primary);
  word-break: break-all;
}

.endpoint-value {
  font-family: 'Roboto Mono', Consolas, monospace;
  font-weight: 400;
}

.debug-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.debug-panel,
.result-panel {
  min-width: 0;
  padding: 20px;
  border: 1px solid var(--hify-border-light);
  border-radius: 8px;
  background: var(--hify-bg-container);
}

.panel-title {
  margin-bottom: 16px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--hify-border-light);
  font-size: 14px;
  font-weight: 700;
  color: var(--hify-text-primary);
}

.field-block {
  margin-bottom: 16px;
}

.field-label {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--hify-text-secondary);
}

.tool-select {
  width: 100%;
}

.tool-option {
  display: flex;
  align-items: baseline;
  gap: 10px;
  min-width: 0;
}

.tool-option-name {
  flex-shrink: 0;
  font-weight: 600;
}

.tool-option-desc {
  font-size: 12px;
  color: var(--hify-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tool-desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--hify-text-tertiary);
}

.params-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.params-header .field-label {
  margin-bottom: 0;
}

.no-params {
  padding: 14px;
  border-radius: 6px;
  background: var(--hify-bg-fill);
  color: var(--hify-text-tertiary);
  font-size: 13px;
}

.json-editor {
  position: relative;
  height: 260px;
  border: 1px solid var(--hify-border-default);
  border-radius: 6px;
  overflow: hidden;
  background: #0f1420;
}

.json-editor.is-error {
  border-color: var(--mcp-danger);
}

.json-highlight,
.json-textarea {
  position: absolute;
  inset: 0;
  margin: 0;
  padding: 12px 14px;
  font-family: 'Roboto Mono', Consolas, monospace;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre;
  overflow: auto;
  tab-size: 2;
}

.json-highlight {
  color: #d5dbe8;
  pointer-events: none;
}

.json-textarea {
  background: transparent;
  color: transparent;
  caret-color: #1677ff;
  border: none;
  outline: none;
  resize: none;
  width: 100%;
  height: 100%;
}

.json-textarea::selection {
  background: rgba(22, 119, 255, 0.35);
  color: transparent;
}

.json-highlight :deep(.json-key) {
  color: #69b1ff;
}

.json-highlight :deep(.json-string) {
  color: #7bd88f;
}

.json-highlight :deep(.json-number) {
  color: #ffb86c;
}

.json-highlight :deep(.json-bool) {
  color: #d8a0ff;
}

.json-highlight :deep(.json-null) {
  color: #ff7a93;
}

.json-error {
  margin: 8px 0 0;
  color: var(--mcp-danger);
  font-size: 12px;
}

.schema-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.schema-field {
  min-width: 0;
}

.schema-label {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
  color: var(--hify-text-secondary);
}

.required-mark {
  color: var(--mcp-danger);
}

.number-input {
  width: 100%;
}

.call-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
}

.status-hint {
  font-size: 12px;
  color: var(--hify-text-tertiary);
}

.status-hint.is-error {
  color: var(--mcp-danger);
}

.result-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 260px;
  border: 1px dashed var(--hify-border-default);
  border-radius: 6px;
}

.result-empty-text {
  color: var(--hify-text-tertiary);
  font-size: 13px;
}

.result-body {
  border-radius: 6px;
  overflow: hidden;
}

.result-body.is-success {
  border: 1px solid rgba(82, 196, 26, 0.4);
}

.result-body.is-error {
  border: 1px solid rgba(255, 77, 79, 0.5);
}

.result-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  font-size: 12px;
}

.result-body.is-success .result-head {
  background: rgba(82, 196, 26, 0.08);
  color: var(--mcp-success);
}

.result-body.is-error .result-head {
  background: rgba(255, 77, 79, 0.08);
  color: var(--mcp-danger);
}

.result-status {
  font-weight: 700;
}

.result-status-code,
.result-meta {
  color: var(--hify-text-tertiary);
}

.result-text {
  margin: 0;
  max-height: 420px;
  overflow: auto;
  padding: 14px;
  background: #0f1420;
  color: #d5dbe8;
  font-family: 'Roboto Mono', Consolas, monospace;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}

.el-button {
  transition: transform 120ms ease, opacity 120ms ease;
}

.el-button:hover {
  transform: translateY(-1px);
}

.el-button:active {
  transform: translateY(0);
}

@media (max-width: 1200px) {
  .info-card {
    grid-template-columns: 1fr;
  }

  .debug-layout {
    grid-template-columns: 1fr;
  }
}
</style>
