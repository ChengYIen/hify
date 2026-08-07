<script setup lang="ts">
import PageHeader from '@/components/common/PageHeader.vue'
</script>

<template>
  <PageHeader
    title="工作流"
    description="编排多步骤 AI 工作流，支持条件分支和并行执行"
  >
    <template #actions>
      <el-button type="primary">创建工作流</el-button>
    </template>
  </PageHeader>
</template>
