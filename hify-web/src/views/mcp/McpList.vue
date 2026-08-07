<script setup lang="ts">
import PageHeader from '@/components/common/PageHeader.vue'
</script>

<template>
  <PageHeader
    title="MCP 工具"
    description="接入 MCP 工具服务器，扩展 Agent 的能力边界"
  >
    <template #actions>
      <el-button type="primary">接入工具</el-button>
    </template>
  </PageHeader>
</template>
