<script setup lang="ts">
import { useRouter } from 'vue-router'
import PageHeader from '@/components/common/PageHeader.vue'
import HifyTable, { type Column, type PageParams } from '@/components/common/HifyTable.vue'
import { listMcpServers, type McpServerListResponse } from '@/api/mcp'

const router = useRouter()

const columns: Column[] = [
  { label: '名称', prop: 'name', minWidth: 160 },
  { label: 'Endpoint', prop: 'endpoint', minWidth: 260 },
  { label: '启用', slot: 'enabled', width: 90, align: 'center' },
  { label: '工具数', prop: 'toolCount', width: 90, align: 'center' },
  { label: '操作', slot: 'actions', width: 120, fixed: 'right' },
]

function fetchServers(params: PageParams) {
  return listMcpServers({ page: params.page, pageSize: params.pageSize })
}

function goDebug(row: Record<string, unknown>): void {
  const server = row as unknown as McpServerListResponse
  router.push({ path: `/mcp/${server.id}`, query: { tab: 'debug' } })
}
</script>

<template>
  <PageHeader
    title="MCP 工具"
    description="接入 MCP 工具服务器，扩展 Agent 的能力边界"
  />

  <HifyTable :columns="columns" :api="fetchServers">
    <template #enabled="{ row }">
      <el-tag :type="row.enabled ? 'success' : 'info'">
        {{ row.enabled ? '已启用' : '已停用' }}
      </el-tag>
    </template>
    <template #actions="{ row }">
      <el-button type="primary" link @click="goDebug(row)">调试</el-button>
    </template>
  </HifyTable>
</template>
