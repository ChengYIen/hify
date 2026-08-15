<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Delete, Edit, Link, Monitor, Plus, Search } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import {
  createMcpServer,
  deleteMcpServer,
  listMcpServers,
  updateMcpServer,
  type McpServerListResponse,
} from '@/api/mcp'
import { notifyError, notifySuccess, notifyWarning } from '@/utils/notify'

const router = useRouter()

const STORAGE_KEY = 'mcp-tools-list-state'

const loading = ref(false)
const allServers = ref<McpServerListResponse[]>([])
const keyword = ref('')
const statusFilter = ref<'all' | 'enabled' | 'disabled'>('all')
const page = ref(1)
const pageSize = ref(10)

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const saving = ref(false)
const form = reactive({
  name: '',
  endpoint: '',
  description: '',
  enabled: true,
})

const filteredServers = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return allServers.value.filter((server) => {
    const matchesKeyword =
      !kw ||
      (server.name || '').toLowerCase().includes(kw) ||
      (server.endpoint || '').toLowerCase().includes(kw) ||
      (server.description || '').toLowerCase().includes(kw)
    const matchesStatus =
      statusFilter.value === 'all' ||
      (statusFilter.value === 'enabled' ? server.enabled : !server.enabled)
    return matchesKeyword && matchesStatus
  })
})

const total = computed(() => filteredServers.value.length)
const pagedServers = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return filteredServers.value.slice(start, start + pageSize.value)
})

watch(total, (value) => {
  const maxPage = Math.max(1, Math.ceil(value / pageSize.value))
  if (page.value > maxPage) page.value = maxPage
})

function saveState(): void {
  sessionStorage.setItem(
    STORAGE_KEY,
    JSON.stringify({
      page: page.value,
      pageSize: pageSize.value,
      keyword: keyword.value,
      statusFilter: statusFilter.value,
    }),
  )
}

function restoreState(): void {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return
    const saved = JSON.parse(raw) as Partial<{
      page: number
      pageSize: number
      keyword: string
      statusFilter: 'all' | 'enabled' | 'disabled'
    }>
    page.value = saved.page ?? 1
    pageSize.value = saved.pageSize ?? 10
    keyword.value = saved.keyword ?? ''
    statusFilter.value = saved.statusFilter ?? 'all'
  } catch {
    sessionStorage.removeItem(STORAGE_KEY)
  }
}

async function fetchAllServers(): Promise<void> {
  loading.value = true
  try {
    const first = await listMcpServers({ page: 1, pageSize: 500 })
    let list = first.list
    if (first.total > first.list.length) {
      const pages = Math.ceil(first.total / 500)
      for (let p = 2; p <= pages; p += 1) {
        const next = await listMcpServers({ page: p, pageSize: 500 })
        list = list.concat(next.list)
        if (next.list.length === 0) break
      }
    }
    allServers.value = list
  } catch {
    notifyError('加载 MCP Server 列表失败')
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  dialogMode.value = 'create'
  editingId.value = null
  Object.assign(form, { name: '', endpoint: '', description: '', enabled: true })
  dialogVisible.value = true
}

function openEdit(row: McpServerListResponse): void {
  dialogMode.value = 'edit'
  editingId.value = row.id
  Object.assign(form, {
    name: row.name || '',
    endpoint: row.endpoint || '',
    description: row.description || '',
    enabled: row.enabled !== false,
  })
  dialogVisible.value = true
}

async function submitForm(): Promise<void> {
  if (!form.name.trim()) {
    notifyWarning('请填写 MCP Server 名称')
    return
  }
  if (!form.endpoint.trim()) {
    notifyWarning('请填写 Endpoint')
    return
  }
  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      endpoint: form.endpoint.trim(),
      description: form.description.trim() || undefined,
      enabled: form.enabled,
    }
    if (dialogMode.value === 'create') {
      await createMcpServer(payload)
      notifySuccess('新增成功')
    } else if (editingId.value != null) {
      await updateMcpServer(editingId.value, payload)
      notifySuccess('保存成功')
    }
    dialogVisible.value = false
    await fetchAllServers()
  } catch {
    // request util 已提示错误
  } finally {
    saving.value = false
  }
}

async function removeServer(row: McpServerListResponse): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确定删除 MCP Server「${row.name}」吗？删除后该 Server 的工具将无法被 Agent 调用。`,
      '删除确认',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }
  try {
    await deleteMcpServer(row.id)
    notifySuccess('删除成功')
    await fetchAllServers()
  } catch {
    // request util 已提示错误
  }
}

function goDebug(row: McpServerListResponse): void {
  router.push({
    path: `/mcp-tools/${row.id}/debug`,
    query: {
      name: row.name || '',
      endpoint: row.endpoint || '',
    },
  })
}

function copyEndpoint(row: McpServerListResponse): void {
  if (!row.endpoint) return
  navigator.clipboard?.writeText(row.endpoint).then(() => {
    notifySuccess('Endpoint 已复制')
  })
}

watch([keyword, statusFilter, page, pageSize], saveState, { deep: true })

onMounted(() => {
  restoreState()
  fetchAllServers()
})
</script>

<template>
  <div class="mcp-tools-page">
    <div class="page-header-row">
      <div class="page-title-block">
        <h1 class="page-title">MCP工具服务</h1>
        <p class="page-subtitle">管理MCP Server，让Agent能调用外部系统（订单、物流、工单等）</p>
      </div>
    </div>

    <div class="toolbar">
      <el-button type="primary" :icon="Plus" @click="openCreate">新增MCP</el-button>
      <div class="toolbar-right">
        <el-input
          v-model="keyword"
          class="search-input"
          placeholder="搜索名称 / Endpoint"
          clearable
          :prefix-icon="Search"
        />
        <el-select v-model="statusFilter" class="status-select">
          <el-option label="全部" value="all" />
          <el-option label="启用" value="enabled" />
          <el-option label="停用" value="disabled" />
        </el-select>
      </div>
    </div>

    <div class="table-wrap" v-loading="loading">
      <el-table :data="pagedServers" row-key="id" class="mcp-table">
        <el-table-column label="名称" min-width="170">
          <template #default="{ row }">
            <div class="name-cell">
              <span class="name-text">{{ row.name || '未命名' }}</span>
              <span v-if="row.toolCount > 0" class="tool-badge">{{ row.toolCount }} 工具</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="Endpoint" min-width="260">
          <template #default="{ row }">
            <div class="endpoint-cell">
              <span class="endpoint-text">{{ row.endpoint || '—' }}</span>
              <el-button
                v-if="row.endpoint"
                link
                type="primary"
                :icon="Link"
                class="copy-btn"
                @click="copyEndpoint(row)"
              >
                复制
              </el-button>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="描述" min-width="200">
          <template #default="{ row }">
            <span class="desc-text">{{ row.description || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <span class="status-cell" :class="row.enabled ? 'is-enabled' : 'is-disabled'">
              <i class="status-dot" />
              {{ row.enabled ? '启用' : '停用' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <div class="action-cell">
              <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
              <el-button link type="warning" :icon="Monitor" @click="goDebug(row)">测试</el-button>
              <el-button link type="primary" :icon="Monitor" @click="goDebug(row)">调试</el-button>
              <el-button link type="danger" :icon="Delete" @click="removeServer(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无 MCP Server" :image-size="80" />
        </template>
      </el-table>
    </div>

    <div class="pagination-row">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
      />
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增 MCP Server' : '编辑 MCP Server'"
      width="520px"
      destroy-on-close
    >
      <el-form label-position="top">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="例如：财务-退款MCP" maxlength="64" />
        </el-form-item>
        <el-form-item label="Endpoint" required>
          <el-input v-model="form.endpoint" placeholder="http://localhost:9001" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="描述这个 MCP 提供的服务能力"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="form.enabled"
            active-text="启用"
            inactive-text="停用"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">
          {{ dialogMode === 'create' ? '创建' : '保存' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.mcp-tools-page {
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
}

.page-header-row {
  margin-bottom: 20px;
}

.page-title {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  color: var(--hify-text-primary);
  line-height: 1.25;
}

.page-subtitle {
  margin: 8px 0 0;
  font-size: 14px;
  color: var(--hify-text-tertiary);
  line-height: 1.6;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.search-input {
  width: 260px;
}

.status-select {
  width: 130px;
}

.table-wrap {
  overflow-x: auto;
  border: 1px solid var(--hify-border-light);
  border-radius: 8px;
}

.mcp-table {
  min-width: 960px;
}

.name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.name-text {
  font-weight: 600;
  color: var(--hify-text-primary);
}

.tool-badge {
  flex-shrink: 0;
  padding: 1px 7px;
  border-radius: 10px;
  background: var(--mcp-primary-light-9, #f0f7ff);
  color: var(--mcp-primary);
  font-size: 12px;
}

.endpoint-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.endpoint-text {
  font-family: 'Roboto Mono', Consolas, monospace;
  font-size: 13px;
  color: var(--hify-text-secondary);
  word-break: break-all;
}

.copy-btn {
  flex-shrink: 0;
}

.desc-text {
  color: var(--hify-text-secondary);
}

.status-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}

.status-cell.is-enabled {
  color: var(--mcp-success);
}

.status-cell.is-enabled .status-dot {
  background: var(--mcp-success);
  box-shadow: 0 0 0 3px rgba(82, 196, 26, 0.15);
}

.status-cell.is-disabled {
  color: var(--hify-text-tertiary);
}

.status-cell.is-disabled .status-dot {
  background: var(--hify-gray-400);
}

.action-cell {
  display: flex;
  align-items: center;
  gap: 2px;
  white-space: nowrap;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
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
</style>
